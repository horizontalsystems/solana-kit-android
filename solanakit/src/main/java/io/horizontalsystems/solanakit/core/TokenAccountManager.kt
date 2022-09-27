package io.horizontalsystems.solanakit.core

import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.AccountInfo
import com.solana.models.buffer.BufferInfo
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.main.MainStorage
import io.horizontalsystems.solanakit.models.TokenAccount
import java.math.BigDecimal

interface ITokenListener {
    fun onUpdateTokenSyncState(value: SolanaKit.SyncState)
    fun onUpdateTokenBalances(tokenAccounts: List<TokenAccount>)
}

class TokenAccountManager(
    private val rpcClient: Api,
    private val storage: MainStorage
) {

    var syncState: SolanaKit.SyncState = SolanaKit.SyncState.NotSynced(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateTokenSyncState(value)
            }
        }

    var listener: ITokenListener? = null

    fun balance(mintAddress: String): BigDecimal? =
        storage.getTokenAccount(mintAddress)?.balance

    fun start() {
        syncState = SolanaKit.SyncState.Syncing()
    }

    fun stop(error: Throwable? = null) {
        syncState = SolanaKit.SyncState.NotSynced(error ?: SolanaKit.SyncError.NotStarted())
    }

    fun sync(tokenAccounts: List<TokenAccount>? = null) {
        syncState = SolanaKit.SyncState.Syncing()

        val tokenAccounts = tokenAccounts ?: storage.getTokenAccounts()
        storage.saveTokenAccounts(tokenAccounts)
        val publicKeys = tokenAccounts.map { PublicKey.valueOf(it.address) }

        rpcClient.getMultipleAccounts(publicKeys, AccountInfo::class.java) { result ->
            result.onSuccess { result ->
                handleBalance(tokenAccounts, result)
            }

            result.onFailure {
                syncState = SolanaKit.SyncState.NotSynced(it)
            }
        }
    }

    private fun handleBalance(tokenAccounts: List<TokenAccount>, tokenAccountsBufferInfo: List<BufferInfo<AccountInfo>?>) {
        val updatedTokenAccounts = mutableListOf<TokenAccount>()

        for ((index, tokenAccount) in tokenAccounts.withIndex()) {
            tokenAccountsBufferInfo[index]?.let { account ->
                val balance = account.data?.value?.lamports?.toBigDecimal() ?: tokenAccount.balance
                updatedTokenAccounts.add(TokenAccount(tokenAccount.address, tokenAccount.mintAddress, balance))
            }
        }

        storage.saveTokenAccounts(updatedTokenAccounts)
        listener?.onUpdateTokenBalances(updatedTokenAccounts)

        syncState = SolanaKit.SyncState.Synced()
    }

}
