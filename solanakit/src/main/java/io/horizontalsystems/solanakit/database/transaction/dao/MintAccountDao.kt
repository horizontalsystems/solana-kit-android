package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import io.horizontalsystems.solanakit.models.MintAccount

@Dao
interface MintAccountDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(accounts: List<MintAccount>)

}
