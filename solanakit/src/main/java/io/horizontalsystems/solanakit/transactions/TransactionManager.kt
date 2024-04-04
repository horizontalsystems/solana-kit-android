package io.horizontalsystems.solanakit.transactions

import android.util.Log
import com.solana.actions.Action
import com.solana.actions.sendSPLTokens
import com.solana.core.Account
import com.solana.core.PublicKey
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.core.TokenAccountManager
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.Address
import io.horizontalsystems.solanakit.models.FullTokenTransfer
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.TokenAccount
import io.horizontalsystems.solanakit.models.TokenTransfer
import io.horizontalsystems.solanakit.models.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.sol4k.Connection
import org.sol4k.Keypair
import org.sol4k.RpcUrl
import org.sol4k.instruction.TransferInstruction
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TransactionManager(
    private val address: Address,
    private val storage: TransactionStorage,
    private val rpcAction: Action,
    private val tokenAccountManager: TokenAccountManager
) {

    private val addressString = address.publicKey.toBase58()
    private val _transactionsFlow = MutableStateFlow<List<FullTransaction>>(listOf())
    val transactionsFlow: StateFlow<List<FullTransaction>> = _transactionsFlow

    fun allTransactionsFlow(incoming: Boolean?): Flow<List<FullTransaction>> = _transactionsFlow.map { txList ->
        val incoming = incoming ?: return@map txList

        txList.filter { fullTransaction ->
            hasSolTransfer(fullTransaction, incoming) || fullTransaction.tokenTransfers.any { it.tokenTransfer.incoming == incoming }
        }
    }.filter { it.isNotEmpty() }

    fun solTransactionsFlow(incoming: Boolean?): Flow<List<FullTransaction>> = _transactionsFlow.map { txList ->
        txList.filter { hasSolTransfer(it, incoming) }
    }.filter { it.isNotEmpty() }

    fun splTransactionsFlow(mintAddress: String, incoming: Boolean?): Flow<List<FullTransaction>> = _transactionsFlow.map { txList ->
        txList.filter { fullTransaction ->
            hasSplTransfer(mintAddress, fullTransaction.tokenTransfers, incoming)
        }
    }.filter { it.isNotEmpty() }


    suspend fun getAllTransaction(incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> =
        storage.getTransactions(incoming, fromHash, limit)

    suspend fun getSolTransaction(incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> =
        storage.getSolTransactions(incoming, fromHash, limit)

    suspend fun getSplTransaction(mintAddress: String, incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> =
        storage.getSplTransactions(mintAddress, incoming, fromHash, limit)

    suspend fun handle(syncedTransactions: List<FullTransaction>, syncedTokenAccounts: List<TokenAccount>) {
        val existingMintAddresses = mutableListOf<String>()

        if (syncedTransactions.isNotEmpty()) {
            val existingTransactionsMap = storage.getFullTransactions(syncedTransactions.map { it.transaction.hash }).groupBy { it.transaction.hash }
            val transactions = syncedTransactions.map { syncedTx ->
                val existingTx = existingTransactionsMap[syncedTx.transaction.hash]?.firstOrNull()

                if (existingTx == null) syncedTx
                else {
                    val syncedTxHeader = syncedTx.transaction
                    val existingTxHeader = existingTx.transaction

                    FullTransaction(
                        transaction = Transaction(
                            hash = syncedTxHeader.hash,
                            timestamp = syncedTxHeader.timestamp,
                            fee = syncedTxHeader.fee,
                            from = syncedTxHeader.from ?: existingTxHeader.from,
                            to = syncedTxHeader.to ?: existingTxHeader.to,
                            amount = syncedTxHeader.amount ?: existingTxHeader.amount,
                            error = syncedTxHeader.error,
                            pending = false
                        ),
                        tokenTransfers = syncedTx.tokenTransfers.ifEmpty {
                            for (tokenTransfer in existingTx.tokenTransfers) {
                                existingMintAddresses.add(tokenTransfer.mintAccount.address)
                            }

                            existingTx.tokenTransfers
                        }
                    )
                }
            }

            storage.addTransactions(transactions)
            _transactionsFlow.tryEmit(transactions)
        }

        if (syncedTokenAccounts.isNotEmpty() || existingMintAddresses.isNotEmpty()) {
            tokenAccountManager.addAccount(syncedTokenAccounts.toSet().toList(), existingMintAddresses.toSet().toList())
        }
    }

    private fun hasSolTransfer(fullTransaction: FullTransaction, incoming: Boolean?): Boolean {
        val amount = fullTransaction.transaction.amount ?: return false
        val incoming = incoming ?: return true

        return amount > BigDecimal.ZERO &&
                ((incoming && fullTransaction.transaction.to == addressString) || (!incoming && fullTransaction.transaction.from == addressString))
    }

    private fun hasSplTransfer(mintAddress: String, tokenTransfers: List<FullTokenTransfer>, incoming: Boolean?): Boolean =
        tokenTransfers.any { fullTokenTransfer ->
            if (fullTokenTransfer.mintAccount.address != mintAddress) return false
            val incoming = incoming ?: return@any true

            fullTokenTransfer.tokenTransfer.incoming == incoming
        }

    private fun sendTransactionX(transaction: org.sol4k.Transaction): String {
        val encodedTransaction = Base64.getEncoder().encodeToString(transaction.serialize())

        Log.e("e", "encodedTransaction: $encodedTransaction")

        return rpcCall(
            "sendTransaction",
            listOf(
                Json.encodeToJsonElement(encodedTransaction),
                Json.encodeToJsonElement(mapOf(
                    "encoding" to "base64",
                    "skipPreflight" to false,
                    "preflightCommitment" to "confirmed",
                    "maxRetries" to 100
                )),
            )
        )
    }

    private inline fun <reified T, reified I : Any> rpcCall(method: String, params: List<I>): T {
        val connection = URL(RpcUrl.MAINNNET.value).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use {
            val body = Json.encodeToString(
                com.solana.networking.models.RpcRequest(method, params)
            )
            Log.e("e", "send request body: $body")

            it.write(body.toByteArray())
        }
        val responseBody = connection.inputStream.use {
            BufferedReader(InputStreamReader(it)).use { reader ->
                reader.readText()
            }
        }
        connection.disconnect()
        try {
//            val (result) = jsonParser.decodeFromString<RpcResponse<T>>(responseBody)
            Log.e("e", "send response body: $responseBody")
            return "result" as T
        } catch (e: SerializationException) {
            Log.e("e", "send error: $responseBody", e)
//            val (error) = jsonParser.decodeFromString<RpcErrorResponse>(responseBody)
//            throw RpcException(error.code, error.message, responseBody)
            throw IllegalStateException(e.message)
        }
    }


    suspend fun sendSol(toAddress: Address, amount: Long, signerAccount: Account, signer: Signer): FullTransaction =
        suspendCoroutine { continuation ->
            val connection = Connection(RpcUrl.MAINNNET)
            val blockhash = connection.getLatestBlockhash()
            val sender = Keypair.fromSecretKey(signer.privateKey)
            val receiver = org.sol4k.PublicKey(toAddress.toString())
            val instruction = TransferInstruction(sender.publicKey, receiver, lamports = 1000)
            val transaction = org.sol4k.Transaction(blockhash, instruction, feePayer = sender.publicKey)
            transaction.sign(sender)

            try {
                val signature = sendTransactionX(transaction)

                val fullTransaction = FullTransaction(
                    Transaction(
                        signature, Instant.now().epochSecond, SolanaKit.fee,
                        addressString, toAddress.publicKey.toBase58(), amount.toBigDecimal(),
                        pending = true
                    ),
                    listOf()
                )

                storage.addTransactions(listOf(fullTransaction))
                continuation.resume(fullTransaction)
                _transactionsFlow.tryEmit(listOf(fullTransaction))

            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }

//            rpcAction.sendSOL(signerAccount, toAddress.publicKey, amount) { result ->
//                result.onSuccess { transactionHash ->
//                    val fullTransaction = FullTransaction(
//                        Transaction(
//                            transactionHash, Instant.now().epochSecond, SolanaKit.fee,
//                            addressString, toAddress.publicKey.toBase58(), amount.toBigDecimal(),
//                            pending = true
//                        ),
//                        listOf()
//                    )
//
//                    storage.addTransactions(listOf(fullTransaction))
//                    continuation.resume(fullTransaction)
//                    _transactionsFlow.tryEmit(listOf(fullTransaction))
//                }
//
//                result.onFailure {
//                    continuation.resumeWithException(it)
//                }
//            }
        }

    suspend fun sendSpl(mintAddress: Address, toAddress: Address, amount: Long, signerAccount: Account): FullTransaction {
        val mintAddressString = mintAddress.publicKey.toBase58()
        val fullTokenAccount = tokenAccountManager.getFullTokenAccountByMintAddress(mintAddressString) ?: throw Exception("TokenAccount not found for $mintAddressString")
        val tokenAccount = fullTokenAccount.tokenAccount
        val mintAccount = fullTokenAccount.mintAccount

        return suspendCoroutine { continuation ->
            rpcAction.sendSPLTokens(
                mintAddress.publicKey, PublicKey(tokenAccount.address), toAddress.publicKey,
                amount,
                account = signerAccount,
                allowUnfundedRecipient = true
            ) { result ->

                result.onSuccess { transactionHash ->

                    Log.e("e", "send spl success txhash=$transactionHash")

                    val fullTransaction = FullTransaction(
                        Transaction(transactionHash, Instant.now().epochSecond, SolanaKit.fee, pending = true),
                        listOf(
                            FullTokenTransfer(
                                TokenTransfer(transactionHash, mintAddressString, false, amount.toBigDecimal()),
                                mintAccount
                            )
                        )
                    )

                    storage.addTransactions(listOf(fullTransaction))
                    continuation.resume(fullTransaction)
                    _transactionsFlow.tryEmit(listOf(fullTransaction))
                }

                result.onFailure {
                    Log.e("e", "send spl error ${it.message}", it)
                    continuation.resumeWithException(it)
                }
            }
        }
    }

}
