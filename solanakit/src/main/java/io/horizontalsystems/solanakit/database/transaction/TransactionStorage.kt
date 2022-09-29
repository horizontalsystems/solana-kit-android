package io.horizontalsystems.solanakit.database.transaction

import androidx.sqlite.db.SimpleSQLiteQuery
import io.horizontalsystems.solanakit.models.*

class TransactionStorage(
    database: TransactionDatabase,
    private val address: String
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

    suspend fun getTransactions(incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> {
        val condition = incoming?.let {
            if (incoming) "((tx.amount IS NOT NULL AND tx.`to` = '$address') OR tt.incoming)"
            else "((tx.amount IS NOT NULL AND tx.`from` = '$address') OR NOT(tt.incoming))"
        }

        return getTransactions(condition, incoming != null, fromHash, limit)
    }

    suspend fun getSolTransactions(incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> {
        val condition = incoming?.let {
            if (incoming) "(tx.amount IS NOT NULL AND tx.`to` = '$address')"
            else "(tx.amount IS NOT NULL AND tx.`from` = '$address')"
        } ?: "tx.amount IS NOT NULL"

        return getTransactions(condition, false, fromHash, limit)
    }

    suspend fun getSplTransactions(mintAddress: String, incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> {
        val condition = incoming?.let {
            val incomingCondition = if (incoming) "tt.incoming" else "NOT(tt.incoming)"
            "(tt.mintAddress = '$mintAddress' AND $incomingCondition)"
        } ?: "tt.mintAddress = '$mintAddress'"

        return getTransactions(condition, true, fromHash, limit)
    }

    private suspend fun getTransactions(typeCondition: String?, joinTokenTransfers: Boolean, fromHash: String?, limit: Int?): List<FullTransaction> {
        val whereConditions = mutableListOf<String>()
        typeCondition?.let { whereConditions.add(it) }

        fromHash?.let { transactionsDao.get(it) }?.let { fromTransaction ->
            val fromCondition = """
                           (
                                tx.blockTime < ${fromTransaction.blockTime} OR
                                (
                                    tx.blockTime = ${fromTransaction.blockTime} AND
                                    HEX(tx.hash) < "${fromTransaction.hash}"
                                )
                           )
                           """

            whereConditions.add(fromCondition)
        }

        val whereClause = if (whereConditions.isNotEmpty()) "WHERE ${whereConditions.joinToString(" AND ")}" else ""
        val orderClause = "ORDER BY tx.blockTime DESC, HEX(tx.hash) DESC"
        val limitClause = limit?.let { "LIMIT $limit" } ?: ""

        val sqlQuery = """
                      SELECT tx.*
                      FROM `Transaction` AS tx
                      ${if (joinTokenTransfers) "LEFT JOIN TokenTransfer AS tt ON tx.hash = tt.transactionHash" else ""}
                      $whereClause
                      $orderClause
                      $limitClause
                      """

        return transactionsDao.getTransactions(SimpleSQLiteQuery(sqlQuery)).map { it.fullTransaction }
    }

}
