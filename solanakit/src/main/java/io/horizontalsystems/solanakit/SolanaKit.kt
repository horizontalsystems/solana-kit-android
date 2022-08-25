package io.horizontalsystems.solanakit

import android.app.Application
import com.solana.api.Api
import com.solana.core.PublicKey
import com.solana.networking.NetworkingRouter
import com.solana.networking.OkHttpNetworkingRouter
import io.horizontalsystems.solanakit.api.ApiRpcSyncer
import io.horizontalsystems.solanakit.models.RpcSource
import io.horizontalsystems.solanakit.network.ConnectionManager
import java.util.*


class SolanaKit(
    private val connectionManager: ConnectionManager,
) {

    private var started = false


    fun start() {
        if (started) return
        started = true

//        blockchain.start()
//        transactionSyncManager.sync()
    }

    fun stop() {
        started = false
//        blockchain.stop()
//        state.clear()
        connectionManager.stop()
    }

    fun refresh() {
//        blockchain.refresh()
//        transactionSyncManager.sync()
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
            publicKey: PublicKey,
            rpcSource: RpcSource,
            walletId: String
        ): SolanaKit {
            val router = OkHttpNetworkingRouter(rpcSource.endpoint)
            val api: Api = Api(router)
            val connectionManager = ConnectionManager(application)

            val apiRpcSyncer = ApiRpcSyncer(api, connectionManager, 15)

            val kit = SolanaKit(connectionManager)

            return kit
        }
//
//        fun getInstance(
//            application: Application,
//            words: List<String>,
//            passphrase: String = "",
//            providerEndpoint: RPCEndpoint,
//            walletId: String
//        ): SolanaKit {
//            val network = NetworkingRouter(providerEndpoint)
//            val rpcProvider = Solana(network)
//            val connectionManager = ConnectionManager(application)
//
//            val sender = Account.fromMnemonic(words, passphrase)
//            val apiRpcSyncer = ApiRpcSyncer(rpcProvider, connectionManager, 15)
//
//            val kit = SolanaKit(connectionManager)
//
//            return kit
//        }

    }

}
