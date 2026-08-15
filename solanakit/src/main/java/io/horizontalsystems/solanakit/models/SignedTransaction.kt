package io.horizontalsystems.solanakit.models

/**
 * Result of signing a transaction offline (see [io.horizontalsystems.solanakit.Signer.signTransaction]).
 *
 * @property serializedTransaction the fully serialized transaction with this signer's signature applied
 * @property signature this signer's raw 64-byte ed25519 signature
 */
class SignedTransaction(
    val serializedTransaction: ByteArray,
    val signature: ByteArray,
)
