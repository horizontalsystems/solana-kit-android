package io.horizontalsystems.solanakit

import com.solana.core.Account
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic

class Signer {

    companion object {

        fun getInstance(seed: ByteArray): Signer {

            val account = account(privateKey(seed))
            val address = solanaAddress(account.publicKey)

            return Signer()
        }

        fun publicKey(
            words: List<String>,
            passphrase: String = ""
        ): PublicKey {
            val account = account(privateKey(Mnemonic().toSeed(words, passphrase)))
            return account.publicKey
        }

        fun publicKey(
            seed: ByteArray
        ): PublicKey {
            val account = account(privateKey(seed))
            return account.publicKey
        }

        fun privateKey(
            words: List<String>,
            passphrase: String = ""
        ): ByteArray {
            return privateKey(Mnemonic().toSeed(words, passphrase))
        }

        fun privateKey(seed: ByteArray): ByteArray {
            val hdWallet = HDWallet(seed, 501)
            return hdWallet.privateKey("m/44'/501'/0'/0'").privKey.toByteArray()
        }

        fun account(privateKey: ByteArray): Account {
            return HotAccount(privateKey)
        }

        fun solanaAddress(publicKey: PublicKey): String {
            return publicKey.toBase58()
        }

    }

}
