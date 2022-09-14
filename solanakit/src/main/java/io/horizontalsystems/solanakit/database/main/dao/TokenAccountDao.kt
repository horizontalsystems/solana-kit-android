package io.horizontalsystems.solanakit.database.main.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.solanakit.models.TokenAccount

@Dao
interface TokenAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: List<TokenAccount>)

    @Query("SELECT * FROM TokenAccount WHERE mintAddress=:mintAddress LIMIT 1")
    fun get(mintAddress: String): TokenAccount?

    @Query("SELECT * FROM TokenAccount")
    fun getAll(): List<TokenAccount>

}
