package io.horizontalsystems.solanakit.transactions

import io.horizontalsystems.solanakit.core.TokenAccountManager
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class TransactionManager(
    private val address: String,
    private val storage: TransactionStorage,
    private val tokenAccountManager: TokenAccountManager
) {

    private val _transactionsFlow = MutableStateFlow<List<FullTransaction>>(listOf())

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

    fun handle(rpcNodeTxs: List<Transaction>, solscanTxs: List<SolscanExportedTransaction>, mintAccounts: Map<String, MintAccount>): Pair<List<FullTransaction>, MutableList<TokenAccount>> {
        var tokenAccounts = mutableListOf<TokenAccount>()
        var fullTransactions = listOf<FullTransaction>()

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

            fullTransactions = fullTransactionsMap.values.toList()

            if (fullTransactions.isNotEmpty()) {
                storage.addTransactions(fullTransactions)
                _transactionsFlow.tryEmit(fullTransactions)
            }
        }

        tokenAccounts = tokenAccounts.toSet().toMutableList()
        if (tokenAccounts.isNotEmpty()) {
            tokenAccountManager.saveAccounts(tokenAccounts)
        }

        return Pair(fullTransactions, tokenAccounts)
    }

    private fun hasSolTransfer(fullTransaction: FullTransaction, incoming: Boolean?): Boolean {
        val amount = fullTransaction.transaction.amount ?: return false
        val incoming = incoming ?: return true

        return amount > BigDecimal.ZERO &&
                ((incoming && fullTransaction.transaction.to == address) || (!incoming && fullTransaction.transaction.from == address))
    }

    private fun hasSplTransfer(mintAddress: String, tokenTransfers: List<TokenTransfer>, incoming: Boolean?): Boolean =
        tokenTransfers.any { tokenTransfer ->
            if (tokenTransfer.mintAddress != mintAddress) return false
            val incoming = incoming ?: return@any true

            tokenTransfer.incoming == incoming
        }

}
