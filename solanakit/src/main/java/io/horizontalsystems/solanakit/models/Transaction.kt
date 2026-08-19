package io.horizontalsystems.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity
data class Transaction(
    @PrimaryKey
    val hash: String,
    val timestamp: Long,
    val fee: BigDecimal? = null,
    val from: String? = null,
    val to: String? = null,
    val amount: BigDecimal? = null,
    val error: String? = null,
    val pending: Boolean = true,
    val blockHash: String = "",
    val lastValidBlockHeight: Long = 0,
    val base64Encoded: String = "",
    val retryCount: Int = 0,
    // Space-separated RECOGNIZED program ids this transaction invoked (see KnownPrograms), e.g.
    // the Jupiter aggregator — lets clients classify swaps ("Swapped via Jupiter") instead of
    // rendering an unknown multi-transfer transaction. Null when none were recognized.
    val programIds: String? = null,
    // Whether this transaction created an associated token account (SPL ATA program invoked among
    // its top-level instructions), i.e. paid ~0.002 SOL of account rent. Lets clients tell that
    // rent apart from a genuine small SOL transfer riding along an SPL transfer. TRUE/FALSE are
    // derived at parse time; NULL means "unknown" — a row stored before this was tracked — for
    // which clients should fall back to an amount heuristic rather than assume no account was made.
    val createdTokenAccount: Boolean? = null
)
