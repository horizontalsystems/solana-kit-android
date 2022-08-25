package io.horizontalsystems.solanakit.sample.ui.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.solanakit.sample.App
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.sample.Configuration

class BalanceViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

//    private lateinit var solanaKit: SolanaKit

    fun init() {
//        solanaKit = createKit()
    }

    private fun createKit(): SolanaKit {
        val words = Configuration.defaultsWords.split(" ")
        val seed = Signer.publicKey(words, "")

        return SolanaKit.getInstance(
            App.instance, seed,
            Configuration.rpcSource, Configuration.walletId
        )
    }

}
