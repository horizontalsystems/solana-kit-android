package io.horizontalsystems.solanakit.sample.ui.transactions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.solanakit.sample.App
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionsViewModel : ViewModel() {

    private val _transactions = MutableLiveData<List<String>>().apply { listOf<String>() }
    val transactions: LiveData<List<String>> = _transactions

    val dateFormat = SimpleDateFormat("dd/M/yyyy hh:mm:ss")

    init {
        viewModelScope.launch {
            val txs = App.instance.solanaKit.getAllTransactions().map {
                """
                    Hash: ${it.transaction.hash}
                    Date: ${dateFormat.format(Date(it.transaction.timestamp * 1000))}
                """.trimIndent()
            }

            _transactions.postValue(txs)
        }

    }

    fun getAllTransactions(incoming: Boolean?) {
        viewModelScope.launch {
            val txs = App.instance.solanaKit.getAllTransactions(incoming).map {
                """
                    Hash: ${it.transaction.hash}
                    Date: ${dateFormat.format(Date(it.transaction.timestamp * 1000))}
                """.trimIndent()
            }

            _transactions.postValue(txs)
        }
    }

    fun getSolTransactions(incoming: Boolean?) {
        viewModelScope.launch {
            val txs = App.instance.solanaKit.getSolTransactions(incoming).map {
                """
                    Hash: ${it.transaction.hash}
                    Date: ${dateFormat.format(Date(it.transaction.timestamp * 1000))}
                """.trimIndent()
            }

            _transactions.postValue(txs)
        }
    }

    fun getSplTransactions(incoming: Boolean?) {
        viewModelScope.launch {
            val txs = App.instance.solanaKit.getSplTransactions("AFbX8oGjGpmVFywbVouvhQSRmiW2aR1mohfahi4Y2AdB", incoming).map {
                """
                    Hash: ${it.transaction.hash}
                    Date: ${dateFormat.format(Date(it.transaction.timestamp * 1000))}
                """.trimIndent()
            }

            _transactions.postValue(txs)
        }
    }

}
