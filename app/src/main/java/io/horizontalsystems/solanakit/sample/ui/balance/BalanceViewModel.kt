package io.horizontalsystems.solanakit.sample.ui.balance

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.solanakit.models.Address
import io.horizontalsystems.solanakit.sample.App
import kotlinx.coroutines.launch
import java.math.BigDecimal

class BalanceViewModel : ViewModel() {

    val receiveAddress = MutableLiveData<String>().apply { value = "" }
    val balance = MutableLiveData<String>().apply { value = "" }
    val balanceSyncState = MutableLiveData<String>().apply { value = "" }
    val tokenBalanceSyncState = MutableLiveData<String>().apply { value = "" }
    val transactionsSyncState = MutableLiveData<String>().apply { value = "" }
    val lastBlockHeight = MutableLiveData<String>().apply { value = "" }

    init {
        val kit = App.instance.solanaKit

        viewModelScope.launch {
            kit.balanceFlow.collect {
                balance.postValue("Balance: $it")
            }
        }

        viewModelScope.launch {
            kit.balanceSyncStateFlow.collect {
                balanceSyncState.postValue("BalanceState: $it")
            }
        }

        viewModelScope.launch {
            kit.tokenBalanceSyncStateFlow.collect {
                tokenBalanceSyncState.postValue("TokenBalanceState: $it")
            }
        }

        viewModelScope.launch {
            kit.transactionsSyncStateFlow.collect {
                transactionsSyncState.postValue("TxSyncState: $it")
            }
        }

        viewModelScope.launch {
            kit.lastBlockHeightFlow.collect {
                lastBlockHeight.postValue("LastBlockHeight: $it")
            }
        }

        balance.postValue("Balance: ${kit.balance}")
        receiveAddress.postValue("Address: ${kit.receiveAddress}")
        balanceSyncState.postValue("SyncState: ${kit.syncState}")
        tokenBalanceSyncState.postValue("TokenSyncState: ${kit.tokenBalanceSyncState}")
        transactionsSyncState.postValue("TxSyncState: ${kit.transactionsSyncState}")
        lastBlockHeight.postValue("LastBlockHeight: ${kit.lastBlockHeight}")
    }

    fun start() {
        App.instance.solanaKit.start()
    }

    fun refresh() {
        App.instance.solanaKit.refresh()
    }

    fun stop() {
        App.instance.solanaKit.stop()
    }

}
