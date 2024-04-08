package io.horizontalsystems.solanakit.transactions

import android.util.Log
import com.solana.api.Api
import com.solana.rxsolana.api.getConfirmedTransaction
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.Transaction
import kotlinx.coroutines.rx2.await
import java.util.logging.Logger

class PendingTransactionSyncer(
    private val rpcClient: Api,
    private val storage: TransactionStorage,
    private val transactionManager: TransactionManager
) {
    private val logger = Logger.getLogger("PendingTransactionSyncer")

    suspend fun sync() {
        val updatedTransactions = mutableListOf<Transaction>()

        storage.pendingTransactions().forEach { pendingTx ->
            try {
                Log.e("e", "pendingTx=${pendingTx.hash}")
                val confirmedTransaction = rpcClient.getConfirmedTransaction(pendingTx.hash).await()

                Log.e("e", "pendingTx status =${confirmedTransaction.meta?.status}")

                confirmedTransaction.meta?.let { meta ->
                    updatedTransactions.add(
                        pendingTx.copy(pending = false, error = meta.err?.toString())
                    )
                }
            } catch (error: Throwable) {

                Log.e("e", "pendingTx error", error)

                logger.info("getConfirmedTx exception ${error.message ?: error.javaClass.simpleName}")
            }
        }

        if (updatedTransactions.isNotEmpty()) {
            storage.updateTransactions(updatedTransactions)

            val updatedTxHashes = updatedTransactions.map { it.hash }
            val fullTransactions = storage.getFullTransactions(updatedTxHashes)
            transactionManager.handle(fullTransactions, listOf())
        }
    }

}
