package io.horizontalsystems.solanakit.database.transaction.dao

import androidx.room.*
import io.horizontalsystems.solanakit.models.*

@Dao
interface TokenAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: List<TokenAccount>)

    @Query("SELECT * FROM TokenAccount WHERE mintAddress=:mintAddress LIMIT 1")
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
