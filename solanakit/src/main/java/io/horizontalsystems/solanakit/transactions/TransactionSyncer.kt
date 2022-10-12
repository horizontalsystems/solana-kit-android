package io.horizontalsystems.solanakit.transactions

import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.Mint
import com.solana.programs.TokenProgram
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.*
import io.horizontalsystems.solanakit.noderpc.endpoints.SignatureInfo
import io.horizontalsystems.solanakit.noderpc.endpoints.getSignaturesForAddress
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface ITransactionListener {
    fun onUpdateTransactionSyncState(syncState: SolanaKit.SyncState)
}

class TransactionSyncer(
    private val publicKey: PublicKey,
    private val rpcClient: Api,
    private val solscanClient: SolscanClient,
    private val storage: TransactionStorage,
    private val transactionManager: TransactionManager
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

        val lastTransactionHash = storage.lastNonPendingTransaction()?.hash
        val fromTime = storage.getSyncedBlockTime(solscanClient.sourceName)?.blockTime

        try {
            val rpcSignatureInfos = getTransactionsFromRpcNode(lastTransactionHash)
            val solscanExportedTxs = getTransactionsFromSolscan(fromTime?.plus(1L))
            val mintAddresses = solscanExportedTxs.mapNotNull { it.mintAccountAddress }.toSet().toList()
            val mintAccounts = getMintAccounts(mintAddresses)
            val tokenAccounts = getTokenAccounts(solscanExportedTxs, mintAccounts.groupBy { it.address })
            val transactions = merge(rpcSignatureInfos, solscanExportedTxs)

            transactionManager.handle(transactions, mintAccounts, tokenAccounts)

            if (solscanExportedTxs.isNotEmpty()) {
                storage.setSyncedBlockTime(SyncedBlockTime(solscanClient.sourceName, solscanExportedTxs.maxOf { it.blockTime }))
            }

            syncState = SolanaKit.SyncState.Synced()
        } catch (exception: Throwable) {
            syncState = SolanaKit.SyncState.NotSynced(exception)
        }
    }

    private fun merge(rpcSignatureInfos: List<SignatureInfo>, solscanExportedTxs: List<SolscanExportedTransaction>): List<FullTransaction> {
        val transactions = mutableMapOf<String, FullTransaction>()

        for (signatureInfo in rpcSignatureInfos) {
            signatureInfo.signature?.let { signature ->
                signatureInfo.blockTime?.let { blockTime ->
                    val transaction = Transaction(signature, blockTime, error = signatureInfo.err?.toString())
                    transactions[signature] = FullTransaction(transaction, listOf())
                }
            }
        }

        for ((hash, solscanTxs) in solscanExportedTxs.groupBy { it.hash }) {
            val existingTransaction = transactions[hash]?.transaction
            val solscanTx = solscanTxs.first()

            val mergedTransaction = Transaction(
                hash,
                existingTransaction?.timestamp ?: solscanTx.blockTime,
                solscanTx.fee.toBigDecimal(),
                solscanTx.solTransferSource,
                solscanTx.solTransferDestination,
                solscanTx.solAmount?.toBigDecimal(),
                existingTransaction?.error
            )

            val tokenTransfers: List<TokenTransfer> = solscanTxs.mapNotNull { solscanTx ->
                val mintAddress = solscanTx.mintAccountAddress ?: return@mapNotNull null
                val amount = solscanTx.splBalanceChange?.toBigDecimal() ?: return@mapNotNull null

                TokenTransfer(hash, mintAddress, amount > BigDecimal.ZERO, amount)
            }

            transactions[hash] = FullTransaction(mergedTransaction, tokenTransfers)
        }

        return transactions.values.toList()
    }

    // TODO: this method must retrieve recursively
    private suspend fun getTransactionsFromRpcNode(lastTransactionHash: String?) = suspendCoroutine<List<SignatureInfo>> { continuation ->
        rpcClient.getSignaturesForAddress(publicKey, until = lastTransactionHash) { result ->
            result.onSuccess { signatureObjects ->
                continuation.resume(signatureObjects)
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

    private fun getTokenAccounts(solscanExportedTxs: List<SolscanExportedTransaction>, mintAccounts: Map<String, List<MintAccount>>): List<TokenAccount> =
        solscanExportedTxs.mapNotNull { solscanTx ->
            val mintAccount = solscanTx.mintAccountAddress?.let { mintAccounts[it]?.firstOrNull() } ?: return@mapNotNull null
            val tokenAccountAddress = solscanTx.tokenAccountAddress ?: return@mapNotNull null

            TokenAccount(tokenAccountAddress, mintAccount.address, BigDecimal.ZERO, mintAccount.decimals)
        }.toSet().toMutableList()

    private suspend fun getMintAccounts(mintAddresses: List<String>) = suspendCoroutine<List<MintAccount>> { continuation ->
        if (mintAddresses.isEmpty()) {
            continuation.resume(listOf())
            return@suspendCoroutine
        }

        rpcClient.getMultipleAccounts(mintAddresses.map { PublicKey.valueOf(it) }, Mint::class.java) { result ->
            result.onSuccess { mintDataList ->
                val mintAccounts = mutableListOf<MintAccount>()

                for ((index, mintAddress) in mintAddresses.withIndex()) {
                    mintDataList[index]?.let { account ->
                        val owner = account.owner
                        val mint = account.data?.value

                        if (owner != tokenProgramId || mint == null) return@let
                        mintAccounts.add(MintAccount(mintAddress, mint.supply, mint.decimals))
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
