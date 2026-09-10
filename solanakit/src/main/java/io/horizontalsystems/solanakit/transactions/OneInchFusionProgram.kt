package io.horizontalsystems.solanakit.transactions

import java.security.MessageDigest

/**
 * Reads the swapped pair out of a 1inch Fusion Swap ([KnownPrograms.oneInchFusion]) instruction.
 *
 * A Fusion swap is split across two transactions — the maker's order-create (the sold token moves
 * into escrow) and a resolver's fill (the bought token is delivered) — so each transaction's
 * balance changes show only ONE side of the swap. The pair itself, though, is named by BOTH
 * instructions: their account lists carry `src_mint` and `dst_mint` at fixed positions (Anchor
 * account structs are positional). Surfacing the pair lets a client render the other side of a
 * one-legged Fusion transaction — e.g. the icon of the token still to be delivered.
 *
 * Positions follow the program's `Create` / `Fill` account structs
 * (github.com/1inch/solana-fusion-protocol, programs/fusion-swap/src/lib.rs):
 *   create: system_program, escrow, src_mint, src_token_program, escrow_src_ata, maker,
 *           maker_src_ata, dst_mint, maker_receiver, associated_token_program, protocol_dst_acc,
 *           integrator_dst_acc
 *   fill:   taker, resolver_access, maker, maker_receiver, src_mint, dst_mint, escrow, …
 * Instructions are told apart by their Anchor discriminator (first 8 bytes of
 * sha256("global:<name>")). Native SOL appears as the wrapped-SOL mint on both sides.
 */
internal object OneInchFusionProgram {

    data class SwapMints(val srcMint: String, val dstMint: String)

    private class Layout(val srcMintIndex: Int, val dstMintIndex: Int, val accountCount: Int)

    private val layouts: Map<String, Layout> = mapOf(
        anchorDiscriminator("create") to Layout(srcMintIndex = 2, dstMintIndex = 7, accountCount = 12),
        anchorDiscriminator("fill") to Layout(srcMintIndex = 4, dstMintIndex = 5, accountCount = 17),
    )

    /** The pair named by the first Fusion `create`/`fill` among [instructions]; null when none. */
    fun swapMints(instructions: List<InvokedInstruction>): SwapMints? =
        instructions.firstNotNullOfOrNull { swapMints(it) }

    /**
     * The pair named by [instruction], or null when it is not a Fusion `create`/`fill` or its
     * account list is shorter than the struct (a layout this decoder does not know).
     */
    fun swapMints(instruction: InvokedInstruction): SwapMints? {
        if (instruction.programId != KnownPrograms.oneInchFusion) return null
        if (instruction.data.size < 8) return null

        val layout = layouts[instruction.data.copyOfRange(0, 8).toHex()] ?: return null
        if (instruction.accounts.size < layout.accountCount) return null

        return SwapMints(
            srcMint = instruction.accounts[layout.srcMintIndex],
            dstMint = instruction.accounts[layout.dstMintIndex]
        )
    }

    private fun anchorDiscriminator(instructionName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("global:$instructionName".toByteArray())
            .copyOfRange(0, 8)
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
