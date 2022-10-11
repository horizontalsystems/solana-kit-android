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
            hasSolTransfer(fullTransaction, incoming) || fullTransaction.tokenTransfers.any { it.incoming == incoming }
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

    fun handle(rpcNodeTxs: List<Transaction>, solscanTxs: List<SolscanExportedTransaction>, mintAccounts: Map<String, MintAccount>) {
        var tokenAccounts = mutableListOf<TokenAccount>()

        val tokenTransferTransactions = solscanTxs.filter { it.type == "TokenChange" }
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
                    val mintAccount = solscanTx.mintAccountAddress?.let { mintAccounts[it] } ?: return@mapNotNull null
                    val tokenAccountAddress = solscanTx.tokenAccountAddress
                    val amount = solscanTx.splBalanceChange?.toBigDecimal()
                    val balance = solscanTx.postBalance?.toBigDecimal()?.movePointRight(mintAccount.decimals)

                    if (tokenAccountAddress == null || amount == null || balance == null) {
                        return@mapNotNull null
                    }

                    tokenAccounts.add(TokenAccount(tokenAccountAddress, mintAccount.address, balance, mintAccount.decimals))
                    TokenTransfer(entry.key, mintAccount.address, amount > BigDecimal.ZERO, amount)
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

            if (fullTransactions.isNotEmpty()) {
                storage.addTransactions(fullTransactions)
                _transactionsFlow.tryEmit(fullTransactions)
            }
        }

        tokenAccounts = tokenAccounts.toSet().toMutableList()
        if (tokenAccounts.isNotEmpty()) {
            tokenAccountManager.addAccount(tokenAccounts)
        }
    }

    private fun hasSolTransfer(fullTransaction: FullTransaction, incoming: Boolean?): Boolean {
        val amount = fullTransaction.transaction.amount ?: return false
        val incoming = incoming ?: return true

        return amount > BigDecimal.ZERO &&
                ((incoming && fullTransaction.transaction.to == addressString) || (!incoming && fullTransaction.transaction.from == addressString))
    }

    private fun hasSplTransfer(mintAddress: String, tokenTransfers: List<TokenTransfer>, incoming: Boolean?): Boolean =
        tokenTransfers.any { tokenTransfer ->
            if (tokenTransfer.mintAddress != mintAddress) return false
            val incoming = incoming ?: return@any true

            tokenTransfer.incoming == incoming
        }

    suspend fun sendSol(toAddress: Address, amount: Long, signerAccount: Account): FullTransaction = suspendCoroutine { continuation ->
        rpcAction.sendSOL(signerAccount, toAddress.publicKey, amount) { result ->
            result.onSuccess { transactionHash ->
                val fullTransaction = FullTransaction(
                    Transaction(transactionHash, Instant.now().epochSecond, SolanaKit.fee, addressString, toAddress.publicKey.toBase58(), amount.toBigDecimal().movePointLeft(9)),
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

    suspend fun sendSpl(mintAddress: Address, toAddress: Address, amount: BigDecimal, signerAccount: Account): FullTransaction {
        val mintAddressString = mintAddress.publicKey.toBase58()
        val mintAccount = storage.getMintAccount(mintAddressString) ?: throw Exception("MintAccount not found $mintAddressString")
        val tokenAccount = tokenAccountManager.getTokenAccountByMintAddress(mintAddressString) ?: throw Exception("TokenAccount not found for $mintAddressString")

        return suspendCoroutine { continuation ->
            rpcAction.sendSPLTokens(
                mintAddress.publicKey, PublicKey(tokenAccount.address), toAddress.publicKey,
                amount.movePointRight(mintAccount.decimals).toLong(), true,
                account = signerAccount
            ) { result ->
                result.onSuccess { transactionHash ->
                    val fullTransaction = FullTransaction(
                        Transaction(transactionHash, Instant.now().epochSecond, SolanaKit.fee),
                        listOf(TokenTransfer(transactionHash, mintAddressString, false, amount))
                    )

                    storage.addTransactions(listOf(fullTransaction))
                    continuation.resume(fullTransaction)
                    _transactionsFlow.tryEmit(listOf(fullTransaction))
                    tokenAccountManager.addAccount(listOf(tokenAccount))
                }

                result.onFailure {
                    continuation.resumeWithException(it)
                }
            }
        }
    }

}
