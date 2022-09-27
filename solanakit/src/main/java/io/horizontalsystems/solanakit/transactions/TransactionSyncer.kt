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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

    suspend fun sync() {
        if (syncState is SolanaKit.SyncState.Syncing) return

        syncState = SolanaKit.SyncState.Syncing()

        val lastTransactionHash = storage.lastTransaction()?.hash
        val fromTime = storage.getSyncedBlockTime(solscanClient.sourceName)?.blockTime

        try {
            val rpcNodeTxs = getTransactionsFromRpcNode(lastTransactionHash)
            val solscanTxs = getTransactionsFromSolscan(fromTime?.plus(1L))
            handle(rpcNodeTxs, solscanTxs)

            if (solscanTxs.isNotEmpty()) {
                storage.setSyncedBlockTime(SyncedBlockTime(solscanClient.sourceName, solscanTxs.map { it.blockTime }.sorted().last()))
            }

            syncState = SolanaKit.SyncState.Synced()
        } catch (exception: Throwable) {
            syncState = SolanaKit.SyncState.NotSynced(exception)
        }
    }

    // TODO: this method must retrieve recursively
    suspend fun getTransactionsFromRpcNode(lastTransactionHash: String?) = suspendCoroutine<List<Transaction>> { continuation ->
        rpcClient.getSignaturesForAddress(publicKey, until = lastTransactionHash) { result ->
            result.onSuccess { signatureObjects ->
                val transactions = signatureObjects.mapNotNull { signatureObject ->
                    signatureObject.signature?.let { signature ->
                        signatureObject.blockTime?.let { blockTime ->
                            Transaction(signature, blockTime)
                        }
                    }
                }

                continuation.resume(transactions)
            }

            result.onFailure { exception ->
                continuation.resumeWithException(exception)
            }
        }
    }

    private suspend fun getTransactionsFromSolscan(fromTime: Long?) = suspendCoroutine<List<SolscanExportedTransaction>> { continuation ->
        solscanClient.transactions(publicKey.toBase58(), fromTime) { result ->
            result.onSuccess { solscanTxs ->
                continuation.resume(solscanTxs)
            }

            result.onFailure {
                continuation.resumeWithException(it)
            }
        }
    }

    private suspend fun handle(rpcNodeTxs: List<Transaction>, solscanTxs: List<SolscanExportedTransaction>) {
        val solTransfers = solscanTxs
            .filter { it.type == "SolTransfer" }
            .map { solscanTx ->
                Transaction(
                    solscanTx.hash,
                    solscanTx.blockTime,
                    solscanTx.fee.toBigDecimal(),
                    solscanTx.solTransferSource,
                    solscanTx.solTransferDestination,
                    solscanTx.solAmount?.toBigDecimal()
                )
            }

        val tokenTransferTransactions = solscanTxs.filter { it.type == "TokenChange" }
        val mintAddresses = tokenTransferTransactions.mapNotNull { it.mintAccountAddress }.toSet().toList()

        val mintAccounts = getMintAccounts(mintAddresses)
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
                    firstTransfer.blockTime,
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

        if (solTransfers.isNotEmpty() || splTransferTransactions.isNotEmpty() || rpcNodeTxs.isNotEmpty()) {
            val fullTransactionsMap: MutableMap<String, FullTransaction> = mutableMapOf()

            for (transaction in rpcNodeTxs) {
                fullTransactionsMap[transaction.hash] = FullTransaction(transaction, listOf())
            }

            for (transaction in solTransfers) {
                fullTransactionsMap[transaction.hash] = FullTransaction(transaction, listOf())
            }

            for (fullTransaction in splTransferTransactions) {
                val transaction = fullTransactionsMap[fullTransaction.transaction.hash]?.transaction ?: fullTransaction.transaction
                fullTransactionsMap[transaction.hash] = FullTransaction(transaction, fullTransaction.tokenTransfers)
            }

            val fullTransactions = fullTransactionsMap.values.toList()

            storage.addTransactions(fullTransactions)
            listener?.onTransactionsReceived(fullTransactions)
        }

        if (tokenAccounts.isNotEmpty()) {
            listener?.onUpdateTokenAccounts(tokenAccounts.toSet().toList())
        }
    }

    private suspend fun getMintAccounts(mintAddresses: List<String>) = suspendCoroutine<Map<String, MintAccount>> { continuation ->
        if (mintAddresses.isEmpty()) {
            continuation.resume(mapOf())
            return@suspendCoroutine
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

                continuation.resume(mintAccounts)
            }

            result.onFailure { exception ->
                continuation.resumeWithException(exception)
            }
        }
    }

    companion object {
        val tokenProgramId = TokenProgram.PROGRAM_ID.toBase58()
    }

}
