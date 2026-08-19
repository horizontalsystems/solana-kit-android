package io.horizontalsystems.solanakit

import com.solana.vendor.TweetNaclFast
import io.horizontalsystems.solanakit.transactions.RawTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sol4k.Base58
import org.sol4k.VersionedTransaction
import java.util.Base64

class SignerTest {

    // Deterministic 64-byte BIP39-style seed.
    private val seed = ByteArray(64) { (it + 1).toByte() }
    private val signer = Signer.getInstance(seed)
    private val publicKey = Base58.decode(Signer.address(seed))

    private fun verify(message: ByteArray, signature: ByteArray): Boolean =
        TweetNaclFast.Signature(publicKey, ByteArray(0)).detached_verify(message, signature)

    @Test
    fun sol4kKeypair_matchesAccountPublicKey() {
        // The sol4k Keypair (built from the 32-byte BIP44 seed) and the com.solana account (built
        // from the 64-byte secret key) must derive the SAME public key, otherwise Token-2022 sends
        // would be signed by the wrong key.
        assertEquals(Signer.address(seed), signer.sol4kKeypair.publicKey.toBase58())
    }

    @Test
    fun signMessage_producesVerifiableSignature() {
        val message = "Hello Solana".toByteArray()

        val signature = signer.signMessage(message)

        assertEquals(64, signature.size)
        assertTrue("signature must verify against the signer's public key", verify(message, signature))
    }

    @Test
    fun isTransactionMessage_detectsSerializedMessages() {
        // The message bytes of a real transaction are exactly what signTransaction signs; feeding
        // them to signMessage would forge a broadcastable transaction signature, so they must be
        // detected (both legacy and v0).
        val v0Message = VersionedTransaction.from(jupiterV0Tx).message.serialize()
        val legacyMessage = VersionedTransaction.from(voteLegacyTx).message.serialize()

        assertTrue(signer.isTransactionMessage(v0Message))
        assertTrue(signer.isTransactionMessage(legacyMessage))
    }

    @Test
    fun isTransactionMessage_ignoresGenuineMessages() {
        assertFalse(signer.isTransactionMessage("Hello Solana".toByteArray()))
        assertFalse(signer.isTransactionMessage("Sign in to Example\nNonce: 42".toByteArray()))
        assertFalse(signer.isTransactionMessage(ByteArray(0)))
        assertFalse(signer.isTransactionMessage(byteArrayOf(0, 1, 2, 3, 4, 5)))
    }

    @Test(expected = TransactionMessageSignRefusedException::class)
    fun signMessage_refusesSerializedTransactionMessage() {
        val message = VersionedTransaction.from(jupiterV0Tx).message.serialize()
        signer.signMessage(message)
    }

    @Test
    fun signTransaction_v0() {
        val signed = signer.signTransaction(Base64.getDecoder().decode(jupiterV0Tx))

        assertEquals(64, signed.signature.size)
        // Re-parsing the serialized output proves it is a well-formed signed transaction.
        RawTransactionParser.parse(signed.serializedTransaction)
    }

    @Test
    fun signTransaction_legacy() {
        val signed = signer.signTransaction(Base64.getDecoder().decode(voteLegacyTx))

        assertEquals(64, signed.signature.size)
        RawTransactionParser.parse(signed.serializedTransaction)
    }

    companion object {
        // Mainnet Jupiter v6 swap, V0 message (with address lookup tables).
        private const val jupiterV0Tx =
            "ARuHvJNIL3Hufj/Jc0F2YK0kr/cY4/SfKn/QkYoKKuyi4L08LKUI0YwiECaCjgw2Y/2e5gC+Y0eEPcyqufPsyQ2AAQAECPSSlhqepX6/HrSS46MZovr/QNtZ9HF2x2ttHRJ0cznBM3emP5VTGuvhVAtCeCFtGVgsDuHOMh6LF14zatClMW1jPVMTQqan3xZwEdYvX58aLu6Xdy1fU3GBOPaSqvHHUMtqa1FW+FdtqQgM7bdPDCFOZ5j9Uk7MU5Q5jdUawfWwAwZGb+UhFzL/7K26csOb57yM5bvF9xJrLEObOkAAAAAEedVb8jHAbu50xW7OaBUH/bGy3qP0jlECsc2iVrwTj7Q/+if11/ZKdMCbHylYed5LCas238ndUUsyGqezjOXo+b7SB/D/xQH0I0yKJWMQqWtTyG+Uc19GnNIpb6dTvEe6rZDrB3d48x0OqLR+nR+Huz2kG440vpEuYzUGISDF0wMEAAUCxB4BAAQACQP/yBQAAAAAAAUYAAIBDw0MDAUGBQMOAAgKCQECDAwLEAUHKLtk+swxxK8UV3q0AwAAAAAl6jUAAAAAAGQAZAAAAAEAAACNABAnAAEBrt8BFUgV9dJh9BWPGETzkUh+QxeZ4SQ+ttVPXt2PgJEDVldPBlADU1IJVA=="

        // Mainnet vote transaction, legacy message.
        private const val voteLegacyTx =
            "AdDy30G1yg1+wTQIzNT0Dc5BbabZcAh0iYIatn6wB7okOFhPl3RTzvucRkW39NOs8wc5vKHNyYoEbE8wqQpnIQMBAAEDFh1Q5EoE6hsz33ShzoO6H2XMw0J1VOpVk5rnVJovlWDIQWnKylvMxQTQuqMaTCl13d9yJOiySGGnglI/oM1gggdhSB01dHS7fE12JOvTvbPYNV5z0RBD/A2jU4AAAAAAX9ykmCtU6gUDFJwgP5MQnXvBR7/OLSfGoIZ1laswmL4BAgIBAJQBDgAAAP2hvBkAAAAAHwEfAR4BHQEcARsBGgEZARgBFwEWARUBFAETARIBEQEQAQ8BDgENAQwBCwEKAQkBCAEHAQYBBQEEAQMBAgEBzjhxYNQYHviIUm8mIjBQvEUvtsseDh42M41x1niNW/YBmYdPagAAAADm1RH7hxTjZLqQcKxeFQSa9YEfYj1poVOirkPjzkO2Yg=="
    }
}
