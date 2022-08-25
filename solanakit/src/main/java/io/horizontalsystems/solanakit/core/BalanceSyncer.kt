package io.horizontalsystems.solanakit.core

import com.solana.api.getBalance
import com.solana.api.getBlockHeight
import com.solana.core.PublicKey
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.api.ApiRpcSyncer
import io.horizontalsystems.solanakit.api.SyncerState
import io.reactivex.schedulers.Schedulers

interface IBalanceListener {
    fun onUpdateLastBlockHeight(lastBlockHeight: Long)
    fun onUpdateSyncState(syncState: SolanaKit.SyncState)
    fun onUpdateBalance(balance: Long)
}
class BalanceSyncer(
    private val publicKey: PublicKey,
    private val syncer: ApiRpcSyncer
) {

    var listener: IBalanceListener? = null
    var syncState: SolanaKit.SyncState = SolanaKit.SyncState.NotSynced(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateSyncState(value)
            }
        }

    fun start() {
        syncState = SolanaKit.SyncState.Syncing()
        syncer.start()
    }

    fun refresh() {
        when (syncer.state) {
            SyncerState.Ready -> {
                syncAccountState()
                syncLastBlockHeight()
            }
            is SyncerState.NotReady -> {
                start()
            }
        }
    }

    fun stop() {
        syncer.stop()
    }

    fun syncAccountState() {
        syncer.api.getBalance(publicKey) {
            it.onSuccess { balance ->
                onUpdateBalance(balance)
                syncState = SolanaKit.SyncState.Synced()
            }

            it.onFailure {
                syncState = SolanaKit.SyncState.NotSynced(it)
            }
        }
    }

    private fun syncLastBlockHeight() {
        syncer.api.getBlockHeight {
            it.onSuccess { lastBlockNumber ->
                onUpdateLastBlockHeight(lastBlockNumber)
            }

            it.onFailure {
                syncState = SolanaKit.SyncState.NotSynced(it)
            }
        }
    }

    private fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
//        storage.saveLastBlockHeight(lastBlockHeight)
        listener?.onUpdateLastBlockHeight(lastBlockHeight)
    }

    private fun onUpdateBalance(balance: Long) {
//        storage.saveAccountState(balance)
        listener?.onUpdateBalance(balance)
    }

}