package io.horizontalsystems.solanakit.core

import android.util.Log
import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.TokenResultObjects
import com.solana.models.buffer.AccountInfo
import com.solana.models.buffer.BufferInfo
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.main.MainStorage
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTokenAccount
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.TokenAccount
import io.horizontalsystems.solanakit.transactions.SolanaFmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

interface ITokenAccountListener {
    fun onUpdateTokenSyncState(value: SolanaKit.SyncState)
}

class TokenAccountManager(
    private val walletAddress: String,
    private val rpcClient: Api,
    private val storage: TransactionStorage,
    private val mainStorage: MainStorage,
    private val solanaFmService: SolanaFmService
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

    @Throws(Exception::class)
    private suspend fun fetchTokenAccounts(walletAddress: String) {

        Log.e("e", "fetchTokenAccounts")
        val tokenAccounts = solanaFmService.tokenAccounts(walletAddress)
        Log.e("e", "tokenAccounts = ${tokenAccounts.joinToString { it.address }}")
        val mintAccounts = tokenAccounts.map { MintAccount(it.mintAddress, it.decimals) }


        storage.saveTokenAccounts(tokenAccounts)
        storage.saveMintAccounts(mintAccounts)
    }

    suspend fun sync(tokenAccounts: List<TokenAccount>? = null) {
        syncState = SolanaKit.SyncState.Syncing()

        var initialSync = mainStorage.isInitialSync()

        Log.e("e", "TokenAccountManager.sync()\n--tokenAccounts = ${tokenAccounts?.joinToString { it.address }}\n--initialSync = $initialSync")


        if (initialSync) {
            try {
                fetchTokenAccounts(walletAddress)
            } catch (e: Exception) {
                initialSync = false
                Log.e("TokenAccountManager", "fetchTokenAccounts error: ", e)
            }
        }

        val tokenAccounts = tokenAccounts ?: storage.getTokenAccounts()
        if (tokenAccounts.isEmpty()) return

        val publicKeys = tokenAccounts.map { PublicKey.valueOf(it.address) }

//        val singles = publicKeys.map { publicKey -> rpcClient.getTokenAccountBalance(publicKey).map { Pair(publicKey, it) } }
//        val multiple = Single.zip(singles) {
//            it.filterIsInstance<Pair<PublicKey, TokenResultObjects.TokenAmountInfo>>()
//        }
//
//        val result = multiple.blockingGet()

//        publicKeys.forEach { publicKey ->
//            rpcClient.getTokenAccountBalance(publicKey) {
//                it.onSuccess {
//                    Log.e("e", "$publicKey success = ${it.amount}")
//                }
//                it.onFailure {
//                    Log.e("e", "$publicKey error", it)
//                }
//            }
//    }
//        handleBalanceX(tokenAccounts, result.map { it.second }, initialSync)

        rpcClient.getMultipleAccounts(publicKeys, AccountInfo::class.java) { result ->
            result.onSuccess { result ->
                Log.e("e", "multipleAccounts success")
                handleBalance(tokenAccounts, result, initialSync)
            }

            result.onFailure {
                Log.e("e", "multipleAccounts error", it)
                syncState = SolanaKit.SyncState.NotSynced(it)
            }
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


    private fun handleBalanceX(
        tokenAccounts: List<TokenAccount>,
        tokenAmountsInfo: List<TokenResultObjects.TokenAmountInfo>,
        initialSync: Boolean
    ) {
        val updatedTokenAccounts = mutableListOf<TokenAccount>()

        for ((index, tokenAccount) in tokenAccounts.withIndex()) {
            tokenAmountsInfo[index].let { tokenAmountInfo ->

                val balance = tokenAmountInfo.uiAmount?.toBigDecimal()?.movePointRight(tokenAmountInfo.decimals) ?: tokenAccount.balance
                updatedTokenAccounts.add(TokenAccount(tokenAccount.address, tokenAccount.mintAddress, balance, tokenAccount.decimals))

                Log.e(
                    "e", "token: ${tokenAccount.address}, " +
                            "amount: ${tokenAmountInfo.amount}, " +
                            "decimals: ${tokenAmountInfo.decimals}, " +
                            "uiAmount: ${tokenAmountInfo.uiAmount?.toBigDecimal()}, " +
                            "uiAmountString: ${tokenAmountInfo.uiAmountString} "
                )
            }
//            tokenAccountsBufferInfo[index]?.let { account ->
//                val balance = account.data?.value?.lamports?.toBigDecimal() ?: tokenAccount.balance
//                updatedTokenAccounts.add(TokenAccount(tokenAccount.address, tokenAccount.mintAddress, balance, tokenAccount.decimals))
//            }
        }

        storage.saveTokenAccounts(updatedTokenAccounts)
        _tokenAccountsUpdatedFlow.tryEmit(storage.getFullTokenAccounts())
        syncState = SolanaKit.SyncState.Synced()
        if (initialSync) {
            handleNewTokenAccounts(updatedTokenAccounts)
        }
    }

    private fun handleBalance(
        tokenAccounts: List<TokenAccount>,
        tokenAccountsBufferInfo: List<BufferInfo<AccountInfo>?>,
        initialSync: Boolean
    ) {
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

    fun addTokenAccount(walletAddress: String, mintAddress: String, decimals: Int) {
        if (!storage.tokenAccountExists(mintAddress)) {
            val userTokenMintAddress = associatedTokenAddress(walletAddress, mintAddress)
            val tokenAccount = TokenAccount(userTokenMintAddress, mintAddress, BigDecimal.ZERO, decimals)
            val mintAccount = MintAccount(mintAddress, decimals)
            storage.addTokenAccount(tokenAccount)
            storage.addMintAccount(mintAccount)
        }
    }

    private fun associatedTokenAddress(
        walletAddress: String,
        tokenMintAddress: String
    ): String {
        return PublicKey.associatedTokenAddress(
            walletAddress = PublicKey(walletAddress),
            tokenMintAddress = PublicKey(tokenMintAddress)
        ).address.toBase58()
    }

}
