package io.horizontalsystems.solanakit.sample.ui.send

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.solanakit.models.Address
import io.horizontalsystems.solanakit.models.FullTokenAccount
import io.horizontalsystems.solanakit.sample.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

class SendViewModel : ViewModel() {

    val tokenAccounts = MutableLiveData<List<FullTokenAccount>>()
    val sendResult = MutableLiveData<String>()
    val isSending = MutableLiveData<Boolean>()

    init {
        loadTokenAccounts()
    }

    private fun loadTokenAccounts() {
        viewModelScope.launch {
            val accounts = App.instance.solanaKit.fungibleTokenAccounts()
            tokenAccounts.postValue(accounts)
        }
    }

    fun sendSol(toAddress: String, amountStr: String) {
        val kit = App.instance.solanaKit
        val signer = App.instance.signer
        viewModelScope.launch(Dispatchers.IO) {
            isSending.postValue(true)
            try {
                val lamports = BigDecimal(amountStr)
                    .multiply(BigDecimal("1000000000"))
                    .toLong()
                val tx = kit.sendSol(Address(toAddress), lamports, signer)
                sendResult.postValue("SOL sent!\nTX: ${tx.transaction.hash}")
            } catch (e: Exception) {
                sendResult.postValue("Error: ${e.message}")
            } finally {
                isSending.postValue(false)
            }
        }
    }

    fun sendSpl(mintAddress: String, toAddress: String, amountStr: String, decimals: Int) {
        val kit = App.instance.solanaKit
        val signer = App.instance.signer
        viewModelScope.launch(Dispatchers.IO) {
            isSending.postValue(true)
            try {
                val rawAmount = BigDecimal(amountStr)
                    .multiply(BigDecimal.TEN.pow(decimals))
                    .toLong()
                val tx = kit.sendSpl(Address(mintAddress), Address(toAddress), rawAmount, signer)
                sendResult.postValue("SPL sent!\nTX: ${tx.transaction.hash}")
            } catch (e: Exception) {
                sendResult.postValue("Error: ${e.message}")
            } finally {
                isSending.postValue(false)
            }
        }
    }
}
