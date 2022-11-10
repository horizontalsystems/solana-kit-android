package io.horizontalsystems.solanakit.transactions

import com.solana.actions.Action
import com.solana.actions.sendSOL
import com.solana.actions.sendSPLTokens
import com.solana.core.Account
import com.solana.core.PublicKey
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.core.TokenAccountManager
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.Instant
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

    suspend fun sendSol(toAddress: Address, amount: Long, signerAccount: Account): FullTransaction = suspendCoroutine { continuation ->
        rpcAction.sendSOL(signerAccount, toAddress.publicKey, amount) { result ->
            result.onSuccess { transactionHash ->
                val fullTransaction = FullTransaction(
                    Transaction(
                        transactionHash, Instant.now().epochSecond, SolanaKit.fee,
                        addressString, toAddress.publicKey.toBase58(), amount.toBigDecimal(),
                        pending = true
                    ),
                    listOf()
                )

                storage.addTransactions(listOf(fullTransaction))
                continuation.resume(fullTransaction)
                _transactionsFlow.tryEmit(listOf(fullTransaction))
            }

            result.onFailure {
                continuation.resumeWithException(it)
            }
        }
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
                    continuation.resumeWithException(it)
                }
            }
        }
    }

}
