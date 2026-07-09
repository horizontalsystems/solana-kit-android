package io.horizontalsystems.solanakit.transactions

/**
 * Program ids the kit RECOGNIZES and surfaces on `Transaction.programIds`, so clients can
 * classify transactions (e.g. render a Jupiter interaction as a swap instead of an unknown
 * multi-transfer). Deliberately a small allowlist — and callers must pass INVOKED program ids
 * (from the transaction's instructions), never raw `accountKeys`: account keys mix programs
 * with ordinary accounts, so presence there does not mean the program ran (a wallet merely
 * RECEIVING the tail of someone else's swap would be mislabeled).
 *
 * Extend by appending — the stored value is just the id string, so older rows stay valid.
 */
object KnownPrograms {
    /** Jupiter aggregator v6. */
    const val jupiterV6 = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"

    /** All recognized program ids. */
    val all: Set<String> = setOf(jupiterV6)

    /**
     * The recognized subset of [candidates], deduplicated (first occurrence wins, order
     * preserved) and space-joined for `Transaction.programIds`; null when none are recognized.
     * Deduplication matters because callers pass one candidate per INSTRUCTION — a program
     * invoked twice in one transaction must still yield a single entry.
     */
    internal fun recognized(candidates: List<String>): String? {
        val hits = candidates.filter { it in all }.distinct()
        return if (hits.isEmpty()) null else hits.joinToString(" ")
    }
}
