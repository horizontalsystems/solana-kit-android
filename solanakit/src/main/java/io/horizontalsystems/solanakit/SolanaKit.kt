package io.horizontalsystems.solanakit

import android.app.Application
import android.content.Context
import com.solana.api.Api
import com.solana.core.PublicKey
import com.solana.networking.Network
import com.solana.networking.OkHttpNetworkingRouter
import io.horizontalsystems.solanakit.core.*
import io.horizontalsystems.solanakit.database.main.MainStorage
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.RpcSource
import io.horizontalsystems.solanakit.models.TokenAccount
import io.horizontalsystems.solanakit.network.ConnectionManager
import io.horizontalsystems.solanakit.noderpc.ApiSyncer
import io.horizontalsystems.solanakit.transactions.SolscanClient
import io.horizontalsystems.solanakit.transactions.TransactionManager
import io.horizontalsystems.solanakit.transactions.TransactionSyncer
import io.reactivex.Single
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.math.BigDecimal
import java.util.*

class SolanaKit(
    private val apiSyncer: ApiSyncer,
    private val balanceManager: BalanceManager,
    private val tokenAccountManager: TokenAccountManager,
    private val transactionManager: TransactionManager,
    private val syncManager: SyncManager,
    rpcSource: RpcSource,
    private val address: String,
) : ISyncListener {

    private var scope: CoroutineScope? = null

    private val _lastBlockHeightFlow = MutableStateFlow(lastBlockHeight)

    private val _balanceSyncStateFlow = MutableStateFlow(syncState)
    private val _balanceFlow = MutableStateFlow(balance)
    private val _tokenBalanceSyncStateFlow = MutableStateFlow(tokenBalanceSyncState)
    private val _tokenBalanceFlow = MutableStateFlow<TokenAccount?>(null)
    private val _transactionsSyncStateFlow = MutableStateFlow(transactionsSyncState)
    private val _transactionsFlow = MutableStateFlow<List<FullTransaction>>(listOf())

    val isMainnet: Boolean = rpcSource.endpoint.network == Network.mainnetBeta
    val receiveAddress = address

    val lastBlockHeight: Long?
        get() = apiSyncer.lastBlockHeight

    val syncState: SyncState
        get() = syncManager.balanceSyncState

    val balance: Long?
        get() = balanceManager.balance

    val tokenBalanceSyncState: SyncState
        get() = syncManager.tokenBalanceSyncState

    fun tokenBalance(mintAddress: String): BigDecimal? =
        tokenAccountManager.balance(mintAddress)

    val transactionsSyncState: SyncState
        get() = syncManager.transactionsSyncState

    val lastBlockHeightFlow: StateFlow<Long?> = _lastBlockHeightFlow

    val balanceSyncStateFlow: StateFlow<SyncState> = _balanceSyncStateFlow
    val balanceFlow: StateFlow<Long?> = _balanceFlow

    val tokenBalanceSyncStateFlow: StateFlow<SyncState> = _tokenBalanceSyncStateFlow
    fun tokenBalanceFlow(mintAddress: String): Flow<BigDecimal> = _tokenBalanceFlow
        .filterNotNull()
        .filter { it.mintAddress == mintAddress }
        .map { it.balance }

    val transactionsSyncStateFlow: StateFlow<SyncState> = _transactionsSyncStateFlow
    val transactionsFlow: StateFlow<List<FullTransaction>> = _transactionsFlow

    fun start() {
        scope = CoroutineScope(Dispatchers.IO)
        scope?.launch {
            syncManager.start(this)
        }
    }

    fun stop() {
        syncManager.stop()
        scope?.cancel()
    }

    fun refresh() {
        if (!scope?.isActive!!) return

        scope?.launch {
            syncManager.refresh(this)
        }
    }

    fun debugInfo(): String {
        val lines = mutableListOf<String>()
        lines.add("ADDRESS: $address")
        return lines.joinToString { "\n" }
    }

    fun statusInfo(): Map<String, Any> {
        val statusInfo = LinkedHashMap<String, Any>()

        return statusInfo
    }

    override fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        _lastBlockHeightFlow.tryEmit(lastBlockHeight)
    }

    override fun onUpdateBalanceSyncState(syncState: SyncState) {
        _balanceSyncStateFlow.tryEmit(syncState)
    }

    override fun onUpdateBalance(balance: Long) {
        _balanceFlow.tryEmit(balance)
    }

    override fun onUpdateTokenSyncState(syncState: SyncState) {
        _tokenBalanceSyncStateFlow.tryEmit(syncState)
    }

    override fun onUpdateTokenBalances(tokenAccounts: List<TokenAccount>) {
        tokenAccounts.forEach {
            _tokenBalanceFlow.tryEmit(it)
        }
    }

    override fun onUpdateTransactionSyncState(syncState: SyncState) {
        _transactionsSyncStateFlow.tryEmit(syncState)
    }

    override fun onUpdateTransactions(transactions: List<FullTransaction>) {
        _transactionsFlow.tryEmit(transactions)
    }

    fun getFullTransactionsAsync(onlySolTransfers: Boolean = false, incoming: Boolean? = null, fromHash: ByteArray? = null, limit: Int? = null): Single<List<FullTransaction>> {
        return transactionManager.getFullTransactionAsync(onlySolTransfers, incoming, fromHash, limit)
    }

    fun getFullTransactionsAsync(splTokenAddress: String, incoming: Boolean? = null, fromHash: ByteArray? = null, limit: Int? = null): Single<List<FullTransaction>> {
        TODO("Not yet implemented")
    }

    sealed class SyncState {
        class Synced : SyncState()
        class NotSynced(val error: Throwable) : SyncState()
        class Syncing(val progress: Double? = null) : SyncState()

        override fun toString(): String = when (this) {
            is Syncing -> "Syncing ${progress?.let { "${it * 100}" } ?: ""}"
            is NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
            else -> this.javaClass.simpleName
        }

        override fun equals(other: Any?): Boolean {
            if (other !is SyncState)
                return false

            if (other.javaClass != this.javaClass)
                return false

            if (other is Syncing && this is Syncing) {
                return other.progress == this.progress
            }

            return true
        }

        override fun hashCode(): Int {
            if (this is Syncing) {
                return Objects.hashCode(this.progress)
            }
            return Objects.hashCode(this.javaClass.name)
        }
    }

    open class SyncError : Exception() {
        class NotStarted : SyncError()
        class NoNetworkConnection : SyncError()
    }

    companion object {

        fun getInstance(
            application: Application,
            address: String,
            rpcSource: RpcSource,
            walletId: String
        ): SolanaKit {
            val router = OkHttpNetworkingRouter(rpcSource.endpoint)
            val connectionManager = ConnectionManager(application)

            val mainDatabase = SolanaDatabaseManager.getMainDatabase(application, walletId)
            val mainStorage = MainStorage(mainDatabase)

            val rpcApiClient = Api(router)
            val apiSyncer = ApiSyncer(rpcApiClient, 15, connectionManager, mainStorage)
            val publicKey = PublicKey(address)

            val balanceManager = BalanceManager(publicKey, rpcApiClient, mainStorage)
            val tokenAccountManager = TokenAccountManager(rpcApiClient, mainStorage)

            val transactionDatabase = SolanaDatabaseManager.getTransactionDatabase(application, walletId)
            val transactionStorage = TransactionStorage(transactionDatabase)
            val solscanClient = SolscanClient(OkHttpClient())
            val transactionManager = TransactionManager(transactionStorage)
            val transactionSyncer = TransactionSyncer(publicKey, rpcApiClient, solscanClient, transactionStorage)

            val syncManager = SyncManager(apiSyncer, balanceManager, transactionSyncer, tokenAccountManager)

            val kit = SolanaKit(apiSyncer, balanceManager, tokenAccountManager, transactionManager, syncManager, rpcSource, address)
            syncManager.listener = kit

            return kit
        }

        fun clear(context: Context, walletId: String) {
            SolanaDatabaseManager.clear(context, walletId)
        }

        private fun loggingHttpClient(): OkHttpClient {
            val consoleLogger = object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    println(message)
                }
            }

            val logging = HttpLoggingInterceptor(consoleLogger)
            logging.level = HttpLoggingInterceptor.Level.BODY

            return OkHttpClient.Builder().addInterceptor(logging).build()
        }

    }

}
