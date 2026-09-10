package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import io.horizontalsystems.solanakit.models.*
import io.horizontalsystems.solanakit.models.Transaction

@Dao
interface TransactionsDao {

    @Query("SELECT * FROM `Transaction` WHERE hash = :transactionHash LIMIT 1")
    fun get(transactionHash: String) : Transaction?

    @Query("SELECT * FROM `Transaction` WHERE NOT pending ORDER BY timestamp DESC LIMIT 1")
    fun lastNonPendingTransaction() : Transaction?

    @Query("SELECT * FROM `Transaction` WHERE pending ORDER BY timestamp")
    fun pendingTransactions() : List<Transaction>

    // Immutable per-transaction tags to preserve across full-row rewrites (see
    // TransactionStorage.backfillTags). A row qualifies if it carries ANY tag, since a plain SPL
    // send has no recognized programIds yet may have createdTokenAccount set. The swap pair is
    // only ever set alongside programIds, so it needs no clause of its own.
    @Query("SELECT hash, programIds, createdTokenAccount, swapSrcMint, swapDstMint FROM `Transaction` WHERE hash IN (:hashes) AND (programIds IS NOT NULL OR createdTokenAccount IS NOT NULL)")
    fun getStoredTags(hashes: List<String>): List<HashWithTags>

    data class HashWithTags(
        val hash: String,
        val programIds: String?,
        val createdTokenAccount: Boolean?,
        val swapSrcMint: String?,
        val swapDstMint: String?
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(transactions: List<Transaction>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTokenTransfers(tokenTransfers: List<TokenTransfer>)

    @Update
    fun updateTransactions(transactions: List<Transaction>)

    @RawQuery
    suspend fun getTransactions(query: SupportSQLiteQuery): List<FullTransactionWrapper>

    data class FullTransactionWrapper(
        @Embedded
        val transaction: Transaction,

        @Relation(
            entity = TokenTransfer::class,
            parentColumn = "hash",
            entityColumn = "transactionHash"
        )
        val tokenTransfersWithMintAccounts: List<TokenTransferAndMintAccount>
    ) {

        val fullTransaction: FullTransaction
            get() = FullTransaction(transaction, tokenTransfersWithMintAccounts.map { it.fullTokenTransfer })

    }

    data class TokenTransferAndMintAccount(
        @Embedded
        val tokenTransfer: TokenTransfer,

        @Relation(
            parentColumn = "mintAddress",
            entityColumn = "address"
        )
        val mintAccount: MintAccount
    ) {

        val fullTokenTransfer: FullTokenTransfer
            get() = FullTokenTransfer(tokenTransfer, mintAccount)

    }

}
