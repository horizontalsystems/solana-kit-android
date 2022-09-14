package io.horizontalsystems.solanakit.noderpc

import com.solana.api.Api
import com.solana.api.getBlockHeight
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.main.MainStorage
import io.horizontalsystems.solanakit.network.ConnectionManager
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import kotlin.concurrent.schedule

interface IApiSyncerListener {
    fun didUpdateApiState(state: SyncerState)
    fun didUpdateLastBlockHeight(lastBlockHeight: Long)
}

sealed class SyncerState {
    object Ready : SyncerState()
    class NotReady(val error: Throwable) : SyncerState()
}

class ApiSyncer(
    private val api: Api,
    private val syncInterval: Long,
    private val connectionManager: ConnectionManager,
    private val storage: MainStorage
) {
    private val disposables = CompositeDisposable()
    private var isStarted = false
    private var timer: Timer? = null

    init {
        connectionManager.listener = object : ConnectionManager.Listener {
            override fun onConnectionChange() {
                handleConnectionChange()
            }
        }
    }

    var state: SyncerState = SyncerState.NotReady(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.didUpdateApiState(value)
            }
        }

    var listener: IApiSyncerListener? = null
    val source = "API ${api.router.endpoint.url.host}"

    var lastBlockHeight: Long? = storage.getLastBlockHeight()
        private set

    fun start() {
        isStarted = true

        handleConnectionChange()
    }

    fun stop() {
        isStarted = false

        connectionManager.stop()
        state = SyncerState.NotReady(SolanaKit.SyncError.NotStarted())
        disposables.clear()
        stopTimer()
    }

    fun sync() {
        api.getBlockHeight {
            it.onSuccess { blockHeight ->
                handleBlockHeight(blockHeight)
            }

            it.onFailure { exception ->
                state = SyncerState.NotReady(exception)
            }
        }
    }

    private fun handleBlockHeight(blockHeight: Long) {
        if (this.lastBlockHeight == lastBlockHeight) {
            this.lastBlockHeight = lastBlockHeight
            storage.saveLastBlockHeight(blockHeight)
        }

        listener?.didUpdateLastBlockHeight(blockHeight)
    }

    private fun handleConnectionChange() {
        if (!isStarted) return

        if (connectionManager.isConnected) {
            state = SyncerState.Ready
            startTimer()
        } else {
            state = SyncerState.NotReady(SolanaKit.SyncError.NoNetworkConnection())
            stopTimer()
        }
    }

    private fun startTimer() {
        timer = Timer().apply {
            schedule(0, syncInterval * 1000) {
                sync()
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

}
