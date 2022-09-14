package io.horizontalsystems.solanakit.transactions

import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.Mint
import com.solana.programs.TokenProgram
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.*
import io.horizontalsystems.solanakit.noderpc.endpoints.getSignaturesForAddress

interface ITransactionListener {
    fun onUpdateTransactionSyncState(syncState: SolanaKit.SyncState)
    fun onUpdateTokenAccounts(tokenAccounts: List<TokenAccount>)
    fun onTransactionsReceived(fullTransactions: List<FullTransaction>)
}

class TransactionSyncer(
    private val publicKey: PublicKey,
    private val rpcClient: Api,
    private val solscanClient: SolscanClient,
    private val storage: TransactionStorage
) {
    var syncState: SolanaKit.SyncState = SolanaKit.SyncState.NotSynced(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateTransactionSyncState(value)
            }
        }

    var listener: ITransactionListener? = null

    fun sync() {
        if (syncState is SolanaKit.SyncState.Syncing) return

        syncState = SolanaKit.SyncState.Syncing()

        val lastTransactionHash = storage.lastTransaction()?.hash
        val fromTime = storage.get(solscanClient.sourceName)?.blockTime

        getTransactionsFromRpcNode(lastTransactionHash) { rpcNodeTxs ->
            getTransactionsFromSolscan(fromTime) { solscanTxs ->
                handle(rpcNodeTxs, solscanTxs)
                syncState = SolanaKit.SyncState.Synced()
            }
        }
    }

    // TODO: this method must retrieve recursively
    fun getTransactionsFromRpcNode(lastTransactionHash: String?, onComplete: (List<Transaction>) -> Unit) {
        rpcClient.getSignaturesForAddress(publicKey, before = lastTransactionHash) { result ->
            result.onSuccess { signatureObjects ->
                val transactions = signatureObjects.mapNotNull { signatureObject ->
                    signatureObject.signature?.let { signature ->
                        signatureObject.blockTime?.let { blockTime ->
                            Transaction(signature, blockTime)
                        }
                    }
                }

                onComplete.invoke(transactions)
            }

            result.onFailure { exception ->
                syncState = SolanaKit.SyncState.NotSynced(exception)
            }
        }
    }

    private fun getTransactionsFromSolscan(fromTime: Long?, onComplete: (List<SolscanExportedTransaction>) -> Unit) {
        solscanClient.transactions(publicKey.toBase58(), fromTime) { result ->
            result.onSuccess { solscanTxs ->
                onComplete.invoke(solscanTxs)
            }

            result.onFailure {
                syncState = SolanaKit.SyncState.NotSynced(it)
            }
        }
    }

    private fun handle(rpcNodeTxs: List<Transaction>, solscanTxs: List<SolscanExportedTransaction>) {
        val solTransfers = solscanTxs
            .filter { it.type == "SolTransfer" }
            .map { solscanTx ->
                Transaction(
                    solscanTx.hash,
                    solscanTx.blockTime.toLong(),
                    solscanTx.fee.toBigDecimal(),
                    solscanTx.solTransferSource,
                    solscanTx.solTransferDestination,
                    solscanTx.solAmount?.toBigDecimal()
                )
            }

        if (solTransfers.isNotEmpty()) {
            storage.updateSolTransferTransactions(solTransfers)
        }

        val tokenTransferTransactions = solscanTxs.filter { it.type == "TokenChange" }
        if (tokenTransferTransactions.isEmpty()) return
        val mintAddresses = tokenTransferTransactions.mapNotNull { it.mintAccountAddress }.toSet().toList()

        getMintAccounts(mintAddresses) { mintAccounts ->
            val tokenAccounts = mutableListOf<TokenAccount>()

            if (mintAccounts.isNotEmpty()) {
                storage.addMintAccounts(mintAccounts.values.toList())
            }

            val splTransferTransactions = tokenTransferTransactions
                .groupBy { it.hash }
                .map { entry ->
                    val firstTransfer = entry.value.first()
                    val transaction = Transaction(
                        entry.key,
                        firstTransfer.blockTime.toLong(),
                        firstTransfer.fee.toBigDecimal()
                    )

                    val tokenTransfers: List<TokenTransfer> = entry.value.mapNotNull { solscanTx ->
                        val mintAccount = solscanTx.mintAccountAddress?.let { mintAccounts[solscanTx.mintAccountAddress] }
                        val tokenAccountAddress = solscanTx.tokenAccountAddress
                        val amount = solscanTx.splBalanceChange?.toBigDecimal()
                        val balance = solscanTx.postBalance?.toBigDecimal()

                        if (mintAccount != null && tokenAccountAddress != null && amount != null && balance != null) {
                            tokenAccounts.add(TokenAccount(tokenAccountAddress, mintAccount.address, balance))

                            TokenTransfer(entry.key, mintAccount.address, amount)
                        } else {
                            null
                        }
                    }

                    FullTransaction(transaction, tokenTransfers)
                }

            if (splTransferTransactions.isNotEmpty()) {
                storage.updateSplTransferTransactions(splTransferTransactions)
            }

            if (solTransfers.isNotEmpty() || splTransferTransactions.isNotEmpty() || rpcNodeTxs.isNotEmpty()) {
                val fullTransactions: MutableMap<String, FullTransaction> = mutableMapOf()

                for (transaction in rpcNodeTxs) {
                    fullTransactions[transaction.hash] = FullTransaction(transaction, listOf())
                }

                for (transaction in solTransfers) {
                    fullTransactions[transaction.hash] = FullTransaction(transaction, listOf())
                }

                for (fullTransaction in splTransferTransactions) {
                    val transaction = fullTransactions[fullTransaction.transaction.hash]?.transaction ?: fullTransaction.transaction
                    fullTransactions[transaction.hash] = FullTransaction(transaction, fullTransaction.tokenTransfers)
                }

                listener?.onTransactionsReceived(fullTransactions.values.toList())
            }

            listener?.onUpdateTokenAccounts(tokenAccounts)
        }
    }

    private fun getMintAccounts(mintAddresses: List<String>, onComplete: (Map<String, MintAccount>) -> Unit) {
        if (mintAddresses.isEmpty()) {
            onComplete.invoke(mapOf())
            return
        }

        rpcClient.getMultipleAccounts(mintAddresses.map { PublicKey.valueOf(it) }, Mint::class.java) { result ->
            result.onSuccess { mintDataList ->
                val mintAccounts = mutableMapOf<String, MintAccount>()

                for ((index, mintAddress) in mintAddresses.withIndex()) {
                    mintDataList[index]?.let { account ->
                        val owner = account.owner
                        val mint = account.data?.value

                        if (owner != tokenProgramId || mint == null) return@let
                        mintAccounts[mintAddress] = MintAccount(mintAddress, mint.supply, mint.decimals)
                    }
                }

                onComplete.invoke(mintAccounts)
            }

            result.onFailure { exception ->
                syncState = SolanaKit.SyncState.NotSynced(exception)
            }
        }
    }

    companion object {
        val tokenProgramId = TokenProgram.PROGRAM_ID.toBase58()
        val solDecimals = 8
    }

}
