package io.horizontalsystems.solanakit

import com.solana.core.Account
import com.solana.core.HotAccount
import com.solana.vendor.TweetNaclFast
import com.solana.vendor.bip32.wallet.DerivableType
import com.solana.vendor.bip32.wallet.SolanaBip44
import io.horizontalsystems.solanakit.models.SignedTransaction
import org.sol4k.Base58
import org.sol4k.Keypair
import org.sol4k.VersionedTransaction
import java.util.Base64

class Signer(
    internal val account: Account,
    // sol4k keypair over the same key, used to sign sol4k-built transactions (e.g. the
    // Token-2022 send path). Kept alongside `account` because the two libraries do not share a
    // signing type.
    internal val sol4kKeypair: Keypair,
) {

    /**
     * Signs an arbitrary message with the account's ed25519 key. Used by WalletConnect's
     * `solana_signMessage`. Returns the raw 64-byte signature.
     */
    fun signMessage(message: ByteArray): ByteArray = account.sign(message)

    /**
     * Signs a serialized transaction (legacy or versioned/V0) WITHOUT broadcasting or otherwise
     * altering it, as required by WalletConnect's `solana_signTransaction` /
     * `solana_signAllTransactions` (the dApp broadcasts later and may add co-signatures, so the
     * message bytes must be preserved verbatim — unlike [SolanaKit.sendRawTransaction], which may
     * refresh the blockhash before broadcasting). Returns the fully serialized signed transaction
     * together with this signer's raw 64-byte signature.
     */
    fun signTransaction(serializedTransaction: ByteArray): SignedTransaction {
        val base64Encoded = Base64.getEncoder().encodeToString(serializedTransaction)
        val versionedTx = VersionedTransaction.from(base64Encoded)

        val signature = account.sign(versionedTx.message.serialize())
        versionedTx.addSignature(Base58.encode(signature))

        return SignedTransaction(versionedTx.serialize(), signature)
    }

    companion object {

        fun getInstance(seed: ByteArray): Signer {
            val account = account(privateKey(seed))

            // sol4k's Keypair.fromSecretKey expects the 32-byte ed25519 seed (it calls
            // keyPair_fromSeed), i.e. the BIP44-derived private key — NOT the 64-byte secret
            // key that `privateKey(seed)` returns. Both libraries derive the same public key.
            val derivedPrivateKey = SolanaBip44().getPrivateKeyFromSeed(seed, DerivableType.BIP44CHANGE)
            val sol4kKeypair = Keypair.fromSecretKey(derivedPrivateKey)

            return Signer(account, sol4kKeypair)
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
