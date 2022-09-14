package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.solanakit.models.LastSyncBlockTime

@Dao
interface TransactionSyncerStateDao {

    @Query("SELECT * FROM LastSyncBlockTime WHERE syncSourceName = :syncSourceName LIMIT 1")
    fun get(syncSourceName: String) : LastSyncBlockTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(transactionSyncerState: LastSyncBlockTime)

}
