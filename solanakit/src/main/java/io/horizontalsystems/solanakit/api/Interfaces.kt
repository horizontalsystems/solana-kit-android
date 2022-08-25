package io.horizontalsystems.solanakit.api

sealed class SyncerState {
    object Ready : SyncerState()
    class NotReady(val error: Throwable) : SyncerState()
}

interface IRpcSyncerListener {
    fun didUpdateSyncerState(state: SyncerState)
    fun didUpdateLastBlockHeight(lastBlockHeight: Long)
}
