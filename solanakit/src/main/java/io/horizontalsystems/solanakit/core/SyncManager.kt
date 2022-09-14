package io.horizontalsystems.solanakit.core

import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.TokenAccount
import io.horizontalsystems.solanakit.noderpc.ApiSyncer
import io.horizontalsystems.solanakit.noderpc.IApiSyncerListener
import io.horizontalsystems.solanakit.noderpc.SyncerState
import io.horizontalsystems.solanakit.transactions.ITransactionListener
import io.horizontalsystems.solanakit.transactions.TransactionSyncer

interface ISyncListener {
    fun onUpdateLastBlockHeight(lastBlockHeight: Long)
    fun onUpdateBalance(balance: Long)
    fun onUpdateTokenBalances(tokenAccounts: List<TokenAccount>)
    fun onUpdateTransactions(transactions: List<FullTransaction>)
    fun onUpdateSyncState(syncState: SolanaKit.SyncState)
    fun onUpdateTokenSyncState(syncState: SolanaKit.SyncState)
    fun onUpdateTransactionSyncState(syncState: SolanaKit.SyncState)
}

class SyncManager(
    private val apiSyncer: ApiSyncer,
    private val balanceSyncer: BalanceManager,
    private val transactionSyncer: TransactionSyncer,
    private val tokenAccountSyncer: TokenAccountManager
): IApiSyncerListener, IBalanceListener, ITransactionListener, ITokenListener {

    var listener: ISyncListener? = null

    val balanceSyncState: SolanaKit.SyncState
        get() = balanceSyncer.syncState

    val tokenBalanceSyncState: SolanaKit.SyncState
        get() = tokenAccountSyncer.syncState

    val transactionsSyncState: SolanaKit.SyncState
        get() = transactionSyncer.syncState

    private var started = false

    init {
        balanceSyncer.listener = this
        apiSyncer.listener = this
    }

    fun start() {
        if (started) return
        started = true

        balanceSyncer.start()
        tokenAccountSyncer.start()
        transactionSyncer.sync()
    }

    fun refresh() {
        when (apiSyncer.state) {
            SyncerState.Ready -> {
                balanceSyncer.sync()
                tokenAccountSyncer.sync()
                apiSyncer.sync()
            }
            is SyncerState.NotReady -> {
                start()
            }
        }
    }

    fun stop() {
        started = false

        apiSyncer.stop()
        balanceSyncer.stop()
        tokenAccountSyncer.stop()
    }

    override fun didUpdateApiState(state: SyncerState) {
        when (state) {
            SyncerState.Ready -> {
                balanceSyncer.sync()
            }
            is SyncerState.NotReady -> {
                balanceSyncer.stop(state.error)
            }
        }
    }

    override fun didUpdateLastBlockHeight(lastBlockHeight: Long) {
        listener?.onUpdateLastBlockHeight(lastBlockHeight)
        transactionSyncer.sync()
    }

    override fun onUpdateTokenSyncState(syncState: SolanaKit.SyncState) {
        listener?.onUpdateSyncState(syncState)
    }

    override fun onUpdateTransactionSyncState(syncState: SolanaKit.SyncState) {
        listener?.onUpdateTransactionSyncState(syncState)
    }

    override fun onUpdateSyncState(value: SolanaKit.SyncState) {
        listener?.onUpdateTokenSyncState(value)
    }

    override fun onUpdateTokenAccounts(tokenAccounts: List<TokenAccount>) {
        tokenAccountSyncer.sync(tokenAccounts)
    }

    override fun onUpdateBalance(balance: Long) {
        listener?.onUpdateBalance(balance)
    }

    override fun onUpdateTokenBalances(tokenAccounts: List<TokenAccount>) {
        listener?.onUpdateTokenBalances(tokenAccounts)
    }

    override fun onTransactionsReceived(fullTransactions: List<FullTransaction>) {
        listener?.onUpdateTransactions(fullTransactions)
        balanceSyncer.sync()
    }

}
