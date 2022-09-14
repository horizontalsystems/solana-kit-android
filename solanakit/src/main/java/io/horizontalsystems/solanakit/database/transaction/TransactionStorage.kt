package io.horizontalsystems.solanakit.database.transaction

import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.LastSyncBlockTime
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.Transaction

class TransactionStorage(
    database: TransactionDatabase
) {
    private val syncerStateDao = database.transactionSyncerStateDao()
    private val transactionsDao = database.transactionsDao()
    private val mintAccountDao = database.mintAccountDao()

    fun get(syncerId: String): LastSyncBlockTime? =
        syncerStateDao.get(syncerId)

    fun save(transactionSyncerState: LastSyncBlockTime) {
        syncerStateDao.save(transactionSyncerState)
    }

    fun addTransactions(transactions: List<Transaction>) {
        transactionsDao.insertTransactions(transactions)
    }

    fun lastTransaction(): Transaction? =
        transactionsDao.lastTransaction()

    fun updateSolTransferTransactions(solTransfers: List<Transaction>) {
        transactionsDao.insertTransactions(solTransfers)
    }

    fun updateSplTransferTransactions(transactions: List<FullTransaction>) {
        transactionsDao.insertTransactions(transactions.map { it.transaction })
        transactionsDao.insertTokenTransfers(transactions.map { it.tokenTransfers }.flatten())
    }

    fun addMintAccounts(accounts: List<MintAccount>) {
        mintAccountDao.insert(accounts)
    }

}
