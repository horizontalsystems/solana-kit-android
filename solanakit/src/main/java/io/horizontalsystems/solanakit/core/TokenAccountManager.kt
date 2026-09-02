package io.horizontalsystems.solanakit.core

import com.solana.api.Api
import com.solana.core.PublicKey
import com.solana.models.buffer.AccountInfo
import com.solana.models.buffer.BufferInfo
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.main.MainStorage
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTokenAccount
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.TokenAccount
import io.horizontalsystems.solanakit.transactions.getMultipleAccounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sol4k.Constants.TOKEN_PROGRAM_ID
import java.math.BigDecimal

interface ITokenAccountListener {
    fun onUpdateTokenSyncState(value: SolanaKit.SyncState)
}

class TokenAccountManager(
    private val walletAddress: String,
    private val rpcClient: Api,
    private val rpcUrl: String,
    private val storage: TransactionStorage,
    private val mainStorage: MainStorage
) {

    var syncState: SolanaKit.SyncState = SolanaKit.SyncState.NotSynced(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateTokenSyncState(value)
            }
        }

    var listener: ITokenAccountListener? = null

    private val _newTokenAccountsFlow = MutableStateFlow<List<FullTokenAccount>>(listOf())
    val newTokenAccountsFlow: StateFlow<List<FullTokenAccount>> = _newTokenAccountsFlow

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

    fun stop(error: Throwable? = null) {
        syncState = SolanaKit.SyncState.NotSynced(error ?: SolanaKit.SyncError.NotStarted())
    }

    suspend fun sync(tokenAccounts: List<TokenAccount>? = null) {
        syncState = SolanaKit.SyncState.Syncing()

        val initialSync = mainStorage.isInitialSync()

        val tokenAccounts = tokenAccounts ?: storage.getTokenAccounts()
        if (tokenAccounts.isEmpty()) {
            syncState = SolanaKit.SyncState.Synced()
            return
        }

        val publicKeys = tokenAccounts.map { PublicKey.valueOf(it.address) }
        try {
            val result = rpcClient.getMultipleAccounts(publicKeys, AccountInfo::class.java)
            handleBalance(tokenAccounts, result, initialSync)
        } catch (error: Throwable) {
            syncState = SolanaKit.SyncState.NotSynced(error)
        }

        if (initialSync) {
            mainStorage.saveInitialSync()
        }
    }

    suspend fun addAccount(receivedTokenAccounts: List<TokenAccount>, existingMintAddresses: List<String>) {
        storage.saveTokenAccounts(receivedTokenAccounts)

        val tokenAccountUpdated: List<TokenAccount> = storage.getTokenAccounts(existingMintAddresses) + receivedTokenAccounts
        sync(tokenAccountUpdated.toSet().toList())
        handleNewTokenAccounts(receivedTokenAccounts)
    }

    fun getFullTokenAccountByMintAddress(mintAddress: String): FullTokenAccount? =
        storage.getFullTokenAccount(mintAddress)

    fun tokenAccounts(): List<FullTokenAccount> =
        storage.getFullTokenAccounts()

    private fun handleBalance(
        tokenAccounts: List<TokenAccount>,
        tokenAccountsBufferInfo: List<BufferInfo<AccountInfo>?>,
        initialSync: Boolean
    ) {
        val updatedTokenAccounts = mutableListOf<TokenAccount>()
        val missingOnChain = mutableListOf<TokenAccount>()

        for ((index, tokenAccount) in tokenAccounts.withIndex()) {
            val account = tokenAccountsBufferInfo[index]
            if (account != null) {
                val balance = account.data?.value?.lamports?.toBigDecimal() ?: tokenAccount.balance
                updatedTokenAccounts.add(TokenAccount(tokenAccount.address, tokenAccount.mintAddress, balance, tokenAccount.decimals))
            } else {
                missingOnChain.add(tokenAccount)
            }
        }

        // A stored row that doesn't exist on-chain while ANOTHER account for the same mint does
        // is a stale placeholder — e.g. an ATA derived with the legacy token program for a
        // Token-2022 mint before the derivation was program-aware. Left in place it can shadow
        // the real account in the by-mint balance lookup, showing a zero balance forever.
        // A missing row whose mint has no on-chain account at all is kept: it's a legitimate
        // not-yet-funded ATA awaiting its first transfer.
        val mintsOnChain = updatedTokenAccounts.map { it.mintAddress }.toSet()
        val staleAddresses = missingOnChain.filter { it.mintAddress in mintsOnChain }.map { it.address }
        if (staleAddresses.isNotEmpty()) {
            storage.deleteTokenAccounts(staleAddresses)
        }

        storage.saveTokenAccounts(updatedTokenAccounts)
        _tokenAccountsUpdatedFlow.tryEmit(storage.getFullTokenAccounts())
        syncState = SolanaKit.SyncState.Synced()
        if (initialSync) {
            handleNewTokenAccounts(updatedTokenAccounts)
        }
    }

    private fun handleNewTokenAccounts(tokenAccounts: List<TokenAccount>) {
        val newFullTokenAccounts = mutableListOf<FullTokenAccount>()
        tokenAccounts.forEach { tokenAccount ->
            storage.getFullTokenAccount(tokenAccount.mintAddress)?.let {
                newFullTokenAccounts.add(it)
            }
        }

        _newTokenAccountsFlow.tryEmit(newFullTokenAccounts)
    }

    suspend fun addTokenAccount(walletAddress: String, mintAddress: String, decimals: Int) {
        if (!storage.tokenAccountExists(mintAddress)) {
            val userTokenMintAddress = associatedTokenAddress(walletAddress, mintAddress)
            val tokenAccount = TokenAccount(userTokenMintAddress, mintAddress, BigDecimal.ZERO, decimals)
            val mintAccount = MintAccount(mintAddress, decimals)
            storage.addTokenAccount(tokenAccount)
            storage.addMintAccount(mintAccount)
        }
    }

    // The ATA PDA seeds include the mint's OWNING token program, so a Token-2022 mint (e.g.
    // Pump.fun tokens) has a different associated address than the legacy derivation produces —
    // deriving with the wrong program yields an address that never exists on-chain and a balance
    // stuck at zero. Fetch the mint account's owner to seed the derivation with the right
    // program. On RPC failure fall back to the legacy program (the vast majority of mints):
    // if that guess is wrong for a Token-2022 mint, the stale row is deleted in handleBalance
    // once the real account is observed on-chain.
    private suspend fun associatedTokenAddress(
        walletAddress: String,
        tokenMintAddress: String
    ): String = withContext(Dispatchers.IO) {
        val mint = org.sol4k.PublicKey(tokenMintAddress)
        val tokenProgramId = try {
            org.sol4k.Connection(rpcUrl).getAccountInfo(mint)?.owner ?: TOKEN_PROGRAM_ID
        } catch (e: Throwable) {
            TOKEN_PROGRAM_ID
        }

        org.sol4k.PublicKey.findProgramDerivedAddress(
            org.sol4k.PublicKey(walletAddress),
            mint,
            tokenProgramId
        ).publicKey.toBase58()
    }

}
