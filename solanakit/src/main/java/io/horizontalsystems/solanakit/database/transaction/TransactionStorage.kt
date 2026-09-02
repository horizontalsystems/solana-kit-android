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
    private val tokenAccountDao = database.tokenAccountsDao()

    fun getSyncedBlockTime(syncerId: String): LastSyncedTransaction? =
        syncerStateDao.get(syncerId)

    fun setSyncedBlockTime(syncBlockTime: LastSyncedTransaction) {
        syncerStateDao.save(syncBlockTime)
    }

    fun lastNonPendingTransaction(): Transaction? =
        transactionsDao.lastNonPendingTransaction()

    fun pendingTransactions(): List<Transaction> =
        transactionsDao.pendingTransactions()

    fun updateTransactions(transactions: List<Transaction>) =
        transactionsDao.updateTransactions(backfillTags(transactions))

    fun addTransactions(transactions: List<FullTransaction>) {
        transactionsDao.insertTransactions(backfillTags(transactions.map { it.transaction }))

        val fullTokenTransfers = transactions.map { it.tokenTransfers }.flatten()
        transactionsDao.insertTokenTransfers(fullTokenTransfers.map { it.tokenTransfer })
        mintAccountDao.insert(fullTokenTransfers.map { it.mintAccount }.toSet().toList())
    }

    // Both write paths perform full-row rewrites (REPLACE on insert, all-columns UPDATE on
    // update), so a writer that doesn't carry a tag would silently null a stored one — un-labelling
    // a classified swap (`programIds`) or losing the token-account-rent flag (`createdTokenAccount`).
    // Both tags are immutable once known — a transaction's invoked programs never change — so
    // backfill each from the stored row whenever the incoming one lacks it. One batched SELECT over
    // the missing hashes (restricted to rows that HAVE a tag), not a per-row lookup: history syncs
    // funnel the whole batch through a single write.
    private fun backfillTags(transactions: List<Transaction>): List<Transaction> {
        val missingHashes = transactions.mapNotNull {
            if (it.programIds == null || it.createdTokenAccount == null) it.hash else null
        }
        if (missingHashes.isEmpty()) return transactions

        val stored = missingHashes.chunked(500)
            .flatMap { transactionsDao.getStoredTags(it) }
            .associateBy { it.hash }
        if (stored.isEmpty()) return transactions

        return transactions.map { transaction ->
            val storedTags = stored[transaction.hash] ?: return@map transaction
            transaction.copy(
                programIds = transaction.programIds ?: storedTags.programIds,
                createdTokenAccount = transaction.createdTokenAccount ?: storedTags.createdTokenAccount
            )
        }
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
                                tx.timestamp < ${fromTransaction.timestamp} OR
                                (
                                    tx.timestamp = ${fromTransaction.timestamp} AND
                                    tx.hash < '${fromTransaction.hash}'
                                )
                           )
                           """

            whereConditions.add(fromCondition)
        }

        val whereClause = if (whereConditions.isNotEmpty()) "WHERE ${whereConditions.joinToString(" AND ")}" else ""
        val orderClause = "ORDER BY tx.timestamp DESC, tx.hash DESC"
        val limitClause = limit?.let { "LIMIT $limit" } ?: ""

        val sqlQuery = """
                      SELECT DISTINCT tx.*
                      FROM `Transaction` AS tx
                      ${if (joinTokenTransfers) "LEFT JOIN TokenTransfer AS tt ON tx.hash = tt.transactionHash" else ""}
                      $whereClause
                      $orderClause
                      $limitClause
                      """

        return transactionsDao.getTransactions(SimpleSQLiteQuery(sqlQuery)).map { it.fullTransaction }
    }

    suspend fun getMintAccount(address: String): MintAccount? =
        mintAccountDao.get(address)

    suspend fun getFullTransactions(hashes: List<String>): List<FullTransaction> {
        val sqlQuery = """
                      SELECT tx.*
                      FROM `Transaction` AS tx
                      LEFT JOIN TokenTransfer AS tt ON tx.hash = tt.transactionHash
                      WHERE tx.hash IN (${hashes.joinToString(", ") { "'$it'" }})
                      """

        return transactionsDao.getTransactions(SimpleSQLiteQuery(sqlQuery)).map { it.fullTransaction }
    }

    fun saveTokenAccounts(tokenAccounts: List<TokenAccount>) {
        tokenAccountDao.insert(tokenAccounts)
    }

    fun saveMintAccounts(mintAccounts: List<MintAccount>) {
        mintAccountDao.insert(mintAccounts)
    }

    fun getTokenAccounts(mintAddresses: List<String>? = null): List<TokenAccount> =
        if (mintAddresses == null) tokenAccountDao.getAll()
        else tokenAccountDao.get(mintAddresses)

    fun getFullTokenAccount(mintAddress: String): FullTokenAccount? =
        tokenAccountDao.get(mintAddress)?.fullTokenAccount

    fun getFullTokenAccounts(): List<FullTokenAccount> =
        tokenAccountDao.getAllFullAccounts().map { it.fullTokenAccount }

    fun tokenAccountExists(mintAddress: String): Boolean =
        tokenAccountDao.getByMintAddress(mintAddress) != null

    fun addTokenAccount(tokenAccount: TokenAccount) {
        tokenAccountDao.insert(tokenAccount)
    }

    fun deleteTokenAccounts(addresses: List<String>) {
        tokenAccountDao.delete(addresses)
    }

    fun addMintAccount(mintAccount: MintAccount) {
        mintAccountDao.insert(mintAccount)
    }
}
