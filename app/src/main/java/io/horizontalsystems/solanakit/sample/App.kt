package io.horizontalsystems.solanakit.sample

import android.app.Application
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.SolanaKit

class App : Application() {

    lateinit var solanaKit: SolanaKit

    fun init() {
        solanaKit = createKit()
    }

    private fun createKit(): SolanaKit {
        val words = Configuration.defaultsWords.split(" ")
        val address = Signer.address(words, "")

        val kit = SolanaKit.getInstance(
            instance, address,
            Configuration.rpcSource, Configuration.walletId
        )

        kit.start()

        return kit
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        this.init()
    }

    companion object {
        lateinit var instance: App
            private set
    }

}
