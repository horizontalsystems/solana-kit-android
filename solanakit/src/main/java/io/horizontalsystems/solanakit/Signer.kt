package io.horizontalsystems.solanakit

import com.solana.core.Account
import com.solana.core.HotAccount
import com.solana.vendor.TweetNaclFast
import com.solana.vendor.bip32.wallet.DerivableType
import com.solana.vendor.bip32.wallet.SolanaBip44
import io.horizontalsystems.hdwalletkit.Mnemonic

class Signer {

    companion object {

        fun getInstance(seed: ByteArray): Signer {
            val account = account(privateKey(seed))
            val address = account.publicKey.toBase58()

            return Signer()
        }

        fun address(
            words: List<String>,
            passphrase: String = ""
        ): String {
            val account = account(privateKey(Mnemonic().toSeed(words, passphrase)))
            return account.publicKey.toBase58()
        }

        fun address(seed: ByteArray): String {
            val account = account(privateKey(seed))
            return account.publicKey.toBase58()
        }

        fun privateKey(
            words: List<String>,
            passphrase: String = ""
        ): ByteArray {
            return privateKey(Mnemonic().toSeed(words, passphrase))
        }

        fun privateKey(seed: ByteArray): ByteArray {
            val solanaBip44 = SolanaBip44()
            val privateKey = solanaBip44.getPrivateKeyFromSeed(seed, DerivableType.BIP44CHANGE)
            val keyPair = TweetNaclFast.Signature.keyPair_fromSeed(privateKey)
            return keyPair.secretKey
        }

        private fun account(privateKey: ByteArray): Account {
            return HotAccount(privateKey)
        }

    }

}
