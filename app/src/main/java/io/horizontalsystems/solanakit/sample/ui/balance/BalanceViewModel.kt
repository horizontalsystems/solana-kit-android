package io.horizontalsystems.solanakit.sample.ui.balance

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.solanakit.sample.App
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.sample.Configuration
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class BalanceViewModel : ViewModel() {

    val receiveAddress = MutableLiveData<String>().apply { value = "" }
    val balance = MutableLiveData<String>().apply { value = "" }

    private lateinit var solanaKit: SolanaKit

    private val updatesDisposables = CompositeDisposable()

    fun init() {
        solanaKit = createKit()
    }

    private fun createKit(): SolanaKit {
        val words = Configuration.defaultsWords.split(" ")
        val address = Signer.address(words, "")

        val kit = SolanaKit.getInstance(
            App.instance, address,
            Configuration.rpcSource, Configuration.walletId
        )

        kit.balanceFlowable
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe {
                balance.postValue("Balance: $it")
            }
            .let {
                updatesDisposables.add(it)
            }

        balance.postValue("Balance: ${kit.balance}")
        receiveAddress.postValue("Address: ${kit.receiveAddress}")

        kit.start()

        return kit
    }

}
