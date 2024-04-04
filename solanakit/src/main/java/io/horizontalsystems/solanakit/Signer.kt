package io.horizontalsystems.solanakit

import com.solana.core.Account
import com.solana.core.HotAccount
import com.solana.vendor.TweetNaclFast
import com.solana.vendor.bip32.wallet.DerivableType
import com.solana.vendor.bip32.wallet.SolanaBip44

class Signer(internal val account: Account, internal val privateKey: ByteArray) {

    companion object {

        fun getInstance(seed: ByteArray): Signer {
            val privateKey = privateKey(seed)
            val account = account(privateKey)

            return Signer(account, privateKey)
        }

        fun address(seed: ByteArray): String {
            val account = account(privateKey(seed))
            return account.publicKey.toBase58()
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
