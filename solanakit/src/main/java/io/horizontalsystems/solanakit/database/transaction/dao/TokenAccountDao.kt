package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import io.horizontalsystems.solanakit.models.FullTokenAccount
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.TokenAccount

@Dao
interface TokenAccountDao {

    // Should duplicate rows for a mint ever coexist (e.g. a stale placeholder ATA alongside the
    // real account), prefer the funded one instead of leaving the pick to SQLite's arbitrary row
    // order. balance is stored as TEXT (BigDecimal converter), hence the CAST for numeric order.
    @Query("SELECT * FROM TokenAccount WHERE mintAddress=:address ORDER BY CAST(balance AS REAL) DESC LIMIT 1")
    fun getByMintAddress(address: String): TokenAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tokenAccount: TokenAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: List<TokenAccount>)

    @Query("DELETE FROM TokenAccount WHERE address IN (:addresses)")
    fun delete(addresses: List<String>)

    @Query("SELECT * FROM TokenAccount WHERE mintAddress=:mintAddress ORDER BY CAST(balance AS REAL) DESC LIMIT 1")
    fun get(mintAddress: String): TokenAccountWrapper?

    @Query("SELECT * FROM TokenAccount WHERE mintAddress IN (:mintAddresses)")
    fun get(mintAddresses: List<String>): List<TokenAccount>

    @Query("SELECT * FROM TokenAccount")
    fun getAll(): List<TokenAccount>

    @Query("SELECT * FROM TokenAccount")
    fun getAllFullAccounts(): List<TokenAccountWrapper>

    data class TokenAccountWrapper(
        @Embedded
        val tokenAccount: TokenAccount,

        @Relation(
            parentColumn = "mintAddress",
            entityColumn = "address"
        )
        val mintAccount: MintAccount
    ) {

        val fullTokenAccount: FullTokenAccount
            get() = FullTokenAccount(tokenAccount, mintAccount)

    }

}
