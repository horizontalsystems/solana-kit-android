package io.horizontalsystems.solanakit.models

class SolanaKitState {
    var balance: Long? = null
    var lastBlockHeight: Long? = null

    fun clear() {
        balance = null
        lastBlockHeight = null
    }
}
