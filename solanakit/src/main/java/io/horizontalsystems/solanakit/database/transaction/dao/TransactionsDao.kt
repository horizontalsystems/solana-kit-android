package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.solanakit.models.TokenTransfer
import io.horizontalsystems.solanakit.models.Transaction

@Dao
interface TransactionsDao {

    @Query("SELECT * FROM `Transaction` WHERE hash = :transactionHash LIMIT 1")
    fun get(transactionHash: String) : Transaction?

    @Query("SELECT * FROM `Transaction` ORDER BY blockTime DESC LIMIT 1")
    fun lastTransaction() : Transaction?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransactions(transactions: List<Transaction>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTokenTransfers(tokenTransfers: List<TokenTransfer>)

}
