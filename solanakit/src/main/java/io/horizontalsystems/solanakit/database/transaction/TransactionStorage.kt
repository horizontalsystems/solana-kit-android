package io.horizontalsystems.solanakit.database.transaction

import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.SyncedBlockTime
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.Transaction

class TransactionStorage(
    database: TransactionDatabase
) {
    private val syncerStateDao = database.transactionSyncerStateDao()
    private val transactionsDao = database.transactionsDao()
    private val mintAccountDao = database.mintAccountDao()

    fun getSyncedBlockTime(syncerId: String): SyncedBlockTime? =
        syncerStateDao.get(syncerId)

    fun setSyncedBlockTime(syncBlockTime: SyncedBlockTime) {
        syncerStateDao.save(syncBlockTime)
    }

    fun lastTransaction(): Transaction? =
        transactionsDao.lastTransaction()

    fun addTransactions(transactions: List<FullTransaction>) {
        transactionsDao.insertTransactions(transactions.map { it.transaction })
        transactionsDao.insertTokenTransfers(transactions.map { it.tokenTransfers }.flatten())
    }

    fun addMintAccounts(accounts: List<MintAccount>) {
        mintAccountDao.insert(accounts)
    }

}
