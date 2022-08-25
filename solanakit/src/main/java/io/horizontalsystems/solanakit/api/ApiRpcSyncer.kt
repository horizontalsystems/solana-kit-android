package io.horizontalsystems.solanakit.api

import com.solana.api.Api
import com.solana.api.getBlockHeight
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.network.ConnectionManager
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import kotlin.concurrent.schedule

class ApiRpcSyncer(
    val api: Api,
    private val connectionManager: ConnectionManager,
    private val syncInterval: Long,
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

    var listener: IRpcSyncerListener? = null
    val source = "API ${api.router.endpoint.url.host}"

    var state: SyncerState = SyncerState.NotReady(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.didUpdateSyncerState(value)
            }
        }

    fun start() {
        isStarted = true

        handleConnectionChange()
    }

    fun stop() {
        isStarted = false

        state = SyncerState.NotReady(SolanaKit.SyncError.NotStarted())
        disposables.clear()
        stopTimer()
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
                onFireTimer()
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun onFireTimer() {
        api.getBlockHeight {
            it.onSuccess { blockHeight ->
                listener?.didUpdateLastBlockHeight(blockHeight)
            }

            it.onFailure { exception ->
                state = SyncerState.NotReady(exception)
            }
        }
    }

}
