package io.horizontalsystems.solanakit

import com.solana.core.Account
import com.solana.core.HotAccount
import com.solana.vendor.TweetNaclFast
import com.solana.vendor.bip32.wallet.DerivableType
import com.solana.vendor.bip32.wallet.SolanaBip44
import io.horizontalsystems.solanakit.models.SignedTransaction
import org.sol4k.Base58
import org.sol4k.Keypair
import org.sol4k.TransactionMessage
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
     *
     * SECURITY: refuses a payload that is actually a serialized transaction message (see
     * [isTransactionMessage]). `signMessage` and [signTransaction] are the same ed25519 operation
     * over raw bytes with no domain separation, so a signature over such a "message" is a valid,
     * broadcastable transaction signature. This is a backstop at the key boundary; callers should
     * also check [isTransactionMessage] up front to reject the request before prompting the user.
     */
    fun signMessage(message: ByteArray): ByteArray {
        if (isTransactionMessage(message)) throw TransactionMessageSignRefusedException()
        return account.sign(message)
    }

    /**
     * True when [message] is a serialized Solana transaction message (legacy or versioned) — exactly
     * the byte string [signTransaction] feeds to the ed25519 key. Detects it via an exact
     * deserialize -> serialize round-trip through the same sol4k codec used for signing, so any
     * canonically-encoded (i.e. network-acceptable) transaction message is recognized while genuine
     * off-chain messages, which do not round-trip, are not. Intended for `solana_signMessage`
     * handlers to reject such a payload instead of signing it.
     */
    fun isTransactionMessage(message: ByteArray): Boolean {
        if (message.isEmpty()) return false
        return try {
            TransactionMessage.deserialize(message).serialize().contentEquals(message)
        } catch (e: Exception) {
            false
        }
    }

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

/**
 * Thrown by [Signer.signMessage] when asked to sign bytes that are actually a serialized transaction
 * message, which would produce a broadcastable transaction signature.
 */
class TransactionMessageSignRefusedException : Exception(
    "Refusing to sign: payload is a serialized transaction message, not an off-chain message"
)
