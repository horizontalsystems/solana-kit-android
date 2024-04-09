package io.horizontalsystems.solanakit.transactions

import android.util.Log
import com.solana.api.Api
import com.solana.rxsolana.api.getBlockHeight
import com.solana.rxsolana.api.getConfirmedTransaction
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.Transaction
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withTimeout
import org.sol4k.RpcUrl
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Logger

class PendingTransactionSyncer(
    private val rpcClient: Api,
    private val storage: TransactionStorage,
    private val transactionManager: TransactionManager
) {
    private val logger = Logger.getLogger("PendingTransactionSyncer")

    suspend fun sync() {
        val updatedTransactions = mutableListOf<Transaction>()

        val pendingTransactions = storage.pendingTransactions()

        Log.e("e", "pendingTransactions = ${pendingTransactions.size} ###############################")
        pendingTransactions.forEachIndexed { index, tx ->
            Log.e("e", "pendingTx $index = ${tx.hash}")
        }

        val currentBlockHeight = rpcClient.getBlockHeight().await()

        Log.e("e", "currentBlockHeight = $currentBlockHeight")

        pendingTransactions.forEach { pendingTx ->
            try {
                Log.e(
                    "e",
                    "pendingTx=${pendingTx.hash}, lastValidBlockHeight=${pendingTx.lastValidBlockHeight}, blockHash=${pendingTx.blockHash} +++++++++++++++++++++++++++++++++++"
                )

                val confirmedTransaction = withTimeout(2000) {
                    rpcClient.getConfirmedTransaction(pendingTx.hash).await()
                }

                Log.e("e", "confirmedTx status = ${confirmedTransaction.meta?.status}, error = ${confirmedTransaction.meta?.err?.toString()}")

                confirmedTransaction.meta?.let { meta ->
                    updatedTransactions.add(
                        pendingTx.copy(pending = false, error = meta.err?.toString())
                    )
                }

            } catch (error: Throwable) {
                Log.e("e", "pendingTx error ${pendingTx.hash}, retryCount = ${pendingTx.retryCount}", error)

                if (currentBlockHeight <= pendingTx.lastValidBlockHeight) {
                    sendTransaction(pendingTx.base64Encoded)

                    updatedTransactions.add(
                        pendingTx.copy(retryCount = pendingTx.retryCount + 1)
                    )
                } else {
                    updatedTransactions.add(
                        pendingTx.copy(pending = false, error = "BlockHash expired")
                    )
                }

                logger.info("getConfirmedTx exception ${error.message ?: error.javaClass.simpleName}")
            }
        }

        Log.e("e", "updatedTransactions = ${updatedTransactions.joinToString { it.hash + " - " + it.pending + " retryCount = ${it.retryCount}" }}")
        storage.updateTransactions(updatedTransactions)

        transactionManager.notifyTransactionsUpdate(storage.getFullTransactions(updatedTransactions.map { it.hash }))

        Log.e("e", "pendingTransactions #############################################")

//        if (updatedTransactions.isNotEmpty()) {

//            val updatedTxHashes = updatedTransactions.map { it.hash }
//            val fullTransactions = storage.getFullTransactions(updatedTxHashes)
//            transactionManager.handle(fullTransactions, listOf())
//        }
    }

    private fun sendTransaction(encodedTransaction: String) {
        Log.e("e", "encodedTransaction: $encodedTransaction")

        try {
            val connection = URL(RpcUrl.MAINNNET.value).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use {

                val body = "{" +
                        "\"method\": \"sendTransaction\", " +
                        "\"jsonrpc\": \"2.0\", " +
                        "\"id\": ${System.currentTimeMillis()}, " +
                        "\"params\": [" +
                        "\"$encodedTransaction\", " +
                        "{" +
                        "\"encoding\": \"base64\"," +
                        "\"skipPreflight\": false," +
                        "\"preflightCommitment\": \"confirmed\"," +
                        "\"maxRetries\": 0" +
                        "}" +
                        "]" +
                        "}"

                Log.e("e", "send request body: $body")

                it.write(body.toByteArray())
            }

            val responseBody = connection.inputStream.use {
                BufferedReader(InputStreamReader(it)).use { reader ->
                    reader.readText()
                }
            }
            connection.disconnect()

            Log.e("e", "send response code=${connection.responseCode}, body: $responseBody")
        } catch (e: Throwable) {
            Log.e("e", "send error", e)
        }
    }

}
