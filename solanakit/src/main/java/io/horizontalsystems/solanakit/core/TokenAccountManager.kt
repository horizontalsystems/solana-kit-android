package io.horizontalsystems.solanakit.core

import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.AccountInfo
import com.solana.models.buffer.BufferInfo
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTokenAccount
import io.horizontalsystems.solanakit.models.TokenAccount
import kotlinx.coroutines.flow.*

interface ITokenAccountListener {
    fun onUpdateTokenSyncState(value: SolanaKit.SyncState)
}

class TokenAccountManager(
    private val rpcClient: Api,
    private val storage: TransactionStorage
) {

    var syncState: SolanaKit.SyncState = SolanaKit.SyncState.NotSynced(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateTokenSyncState(value)
            }
        }

    var listener: ITokenAccountListener? = null

    private val _tokenAccountsUpdatedFlow = MutableStateFlow<List<FullTokenAccount>>(listOf())
    val tokenAccountsFlow: StateFlow<List<FullTokenAccount>> = _tokenAccountsUpdatedFlow

    fun tokenBalanceFlow(mintAddress: String): Flow<FullTokenAccount> = _tokenAccountsUpdatedFlow
        .map { tokenAccounts ->
            tokenAccounts.firstOrNull {
                it.mintAccount.address == mintAddress
            }
        }
        .filterNotNull()

    fun fullTokenAccount(mintAddress: String): FullTokenAccount? =
        storage.getFullTokenAccount(mintAddress)

    fun start() {
        syncState = SolanaKit.SyncState.Syncing()
    }

    fun stop(error: Throwable? = null) {
        syncState = SolanaKit.SyncState.NotSynced(error ?: SolanaKit.SyncError.NotStarted())
    }

    fun sync(tokenAccounts: List<TokenAccount>? = null) {
        syncState = SolanaKit.SyncState.Syncing()

        val tokenAccounts = tokenAccounts ?: storage.getTokenAccounts()
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

    fun addAccount(receivedTokenAccounts: List<TokenAccount>, existingMintAddresses: List<String>) {
        storage.saveTokenAccounts(receivedTokenAccounts)

        val tokenAccountUpdated: List<TokenAccount> = storage.getTokenAccounts(existingMintAddresses) + receivedTokenAccounts
        sync(tokenAccountUpdated.toSet().toList())
    }

    fun getFullTokenAccountByMintAddress(mintAddress: String): FullTokenAccount? =
        storage.getFullTokenAccount(mintAddress)

    fun tokenAccounts(): List<FullTokenAccount> =
        storage.getFullTokenAccounts()

    private fun handleBalance(tokenAccounts: List<TokenAccount>, tokenAccountsBufferInfo: List<BufferInfo<AccountInfo>?>) {
        val updatedTokenAccounts = mutableListOf<TokenAccount>()

        for ((index, tokenAccount) in tokenAccounts.withIndex()) {
            tokenAccountsBufferInfo[index]?.let { account ->
                val balance = account.data?.value?.lamports?.toBigDecimal() ?: tokenAccount.balance
                updatedTokenAccounts.add(TokenAccount(tokenAccount.address, tokenAccount.mintAddress, balance, tokenAccount.decimals))
            }
        }

        storage.saveTokenAccounts(updatedTokenAccounts)
        _tokenAccountsUpdatedFlow.tryEmit(storage.getFullTokenAccounts())
        syncState = SolanaKit.SyncState.Synced()
    }

}
