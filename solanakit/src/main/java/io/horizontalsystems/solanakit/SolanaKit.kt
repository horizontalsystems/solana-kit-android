package io.horizontalsystems.solanakit

import android.app.Application
import android.content.Context
import com.solana.api.Api
import com.solana.core.PublicKey
import com.solana.networking.Network
import com.solana.networking.OkHttpNetworkingRouter
import io.horizontalsystems.solanakit.api.ApiRpcSyncer
import io.horizontalsystems.solanakit.core.BalanceSyncer
import io.horizontalsystems.solanakit.core.IBalanceListener
import io.horizontalsystems.solanakit.models.RpcSource
import io.horizontalsystems.solanakit.models.SolanaKitState
import io.horizontalsystems.solanakit.network.ConnectionManager
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import java.util.*

class SolanaKit(
        private val balanceSyncer: BalanceSyncer,
        private val connectionManager: ConnectionManager,
        rpcSource: RpcSource,
        private val address: String,
) : IBalanceListener {

    private var state = SolanaKitState()
    private var started = false

    private val lastBlockHeightSubject = PublishSubject.create<Long>()
    private val syncStateSubject = PublishSubject.create<SyncState>()
    private val balanceSubject = PublishSubject.create<Long>()

    var isMainnet: Boolean = rpcSource.endpoint.network == Network.mainnetBeta

    init {
        state.lastBlockHeight = balanceSyncer.lastBlockHeight
        state.balance = balanceSyncer.balance
        balanceSyncer.listener = this
    }

    val lastBlockHeight: Long?
        get() = state.lastBlockHeight

    val balance: Long?
        get() = state.balance

    val syncState: SyncState
        get() = balanceSyncer.syncState

//    val transactionsSyncState: SyncState
//        get() = transactionSyncManager.syncState

    val receiveAddress: String
        get() = address

    val lastBlockHeightFlowable: Flowable<Long>
        get() = lastBlockHeightSubject.toFlowable(BackpressureStrategy.BUFFER)

    val syncStateFlowable: Flowable<SyncState>
        get() = syncStateSubject.toFlowable(BackpressureStrategy.BUFFER)

//    val transactionsSyncStateFlowable: Flowable<SyncState>
//        get() = transactionSyncManager.syncStateAsync

    val balanceFlowable: Flowable<Long>
        get() = balanceSubject.toFlowable(BackpressureStrategy.BUFFER)

//    val allTransactionsFlowable: Flowable<Pair<List<FullTransaction>, Boolean>>
//        get() = transactionManager.fullTransactionsAsync

    fun start() {
        if (started) return
        started = true

        balanceSyncer.start()
//        transactionSyncManager.sync()
    }

    fun stop() {
        started = false

        balanceSyncer.stop()
        state.clear()
        connectionManager.stop()
    }

    fun refresh() {
        balanceSyncer.refresh()
//        transactionSyncManager.sync()
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
        if (state.lastBlockHeight == lastBlockHeight) return

        state.lastBlockHeight = lastBlockHeight
        lastBlockHeightSubject.onNext(lastBlockHeight)
//        transactionSyncManager.sync()
    }

    override fun onUpdateSyncState(syncState: SyncState) {
        syncStateSubject.onNext(syncState)
    }

    override fun onUpdateBalance(balance: Long) {
        if (state.balance == balance) return

        state.balance = balance
        balanceSubject.onNext(balance)
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
            val api = Api(router)
            val connectionManager = ConnectionManager(application)

            val apiRpcSyncer = ApiRpcSyncer(api, connectionManager, 15)
            val publicKey = PublicKey(address)

            val balanceSyncer = BalanceSyncer(publicKey, apiRpcSyncer)

            val kit = SolanaKit(balanceSyncer, connectionManager, rpcSource, address)

            return kit
        }

        fun clear(context: Context, rpcSource: RpcSource, walletId: String) {

        }

    }

}
