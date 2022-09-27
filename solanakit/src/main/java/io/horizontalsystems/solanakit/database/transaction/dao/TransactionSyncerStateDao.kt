package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.solanakit.models.SyncedBlockTime

@Dao
interface TransactionSyncerStateDao {

    @Query("SELECT * FROM SyncedBlockTime WHERE syncSourceName = :syncSourceName LIMIT 1")
    fun get(syncSourceName: String) : SyncedBlockTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(transactionSyncerState: SyncedBlockTime)

}
