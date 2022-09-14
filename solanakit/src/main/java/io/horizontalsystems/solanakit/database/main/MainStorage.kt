package io.horizontalsystems.solanakit.database.main

import io.horizontalsystems.solanakit.models.BalanceEntity
import io.horizontalsystems.solanakit.models.LastBlockHeightEntity
import io.horizontalsystems.solanakit.models.TokenAccount

class MainStorage(
    private val database: MainDatabase
) {

    fun getLastBlockHeight(): Long? {
        return database.lastBlockHeightDao().getLastBlockHeight()?.height
    }

    fun saveLastBlockHeight(lastBlockHeight: Long) {
        database.lastBlockHeightDao().insert(LastBlockHeightEntity(lastBlockHeight))
    }

    fun saveBalance(balance: Long) {
        database.balanceDao().insert(BalanceEntity(balance))
    }

    fun getBalance(): Long? {
        return database.balanceDao().getBalance()?.lamports
    }

    fun saveTokenAccounts(tokenAccounts: List<TokenAccount>) {
        database.tokenAccountsDao().insert(tokenAccounts)
    }

    fun getTokenAccounts(): List<TokenAccount> =
        database.tokenAccountsDao().getAll()

}
