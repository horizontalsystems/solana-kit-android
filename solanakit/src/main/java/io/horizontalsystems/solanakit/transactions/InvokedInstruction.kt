package io.horizontalsystems.solanakit.transactions

/**
 * A top-level instruction as INVOKED by a transaction, in the shape both parse paths can produce:
 * the confirmed jsonParsed response (`partiallyDecoded` instructions carry base58 accounts and
 * data) and the raw message parsed before broadcast (`RawTransactionParser`). [accounts] are
 * base58 pubkeys in the order the program declares them; [data] is the raw instruction payload.
 */
internal data class InvokedInstruction(
    val programId: String,
    val accounts: List<String>,
    val data: ByteArray,
)
