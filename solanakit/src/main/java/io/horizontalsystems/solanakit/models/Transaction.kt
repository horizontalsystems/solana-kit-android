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
    val createdTokenAccount: Boolean? = null,
    // The swapped pair as NAMED by the swap program's instruction (currently 1inch Fusion, whose
    // `create`/`fill` account lists carry `src_mint`/`dst_mint` — see OneInchFusionProgram), as
    // opposed to what this transaction's balance changes show. They differ for a Fusion swap: it is
    // split across an order-create and a later fill, so each transaction moves only ONE side of the
    // pair, and these let a client name the other side (e.g. draw its icon). Native SOL appears as
    // the wrapped-SOL mint. Null when no recognized program named a pair (Jupiter and LI.FI move
    // both sides in one transaction, so their pair is visible from the transfers).
    val swapSrcMint: String? = null,
    val swapDstMint: String? = null
)
