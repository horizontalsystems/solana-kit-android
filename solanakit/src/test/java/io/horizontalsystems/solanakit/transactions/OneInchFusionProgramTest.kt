package io.horizontalsystems.solanakit.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneInchFusionProgramTest {

    private val wsol = "So11111111111111111111111111111111111111112"
    private val usdc = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    // Anchor discriminators = sha256("global:<name>")[0..8], as observed on mainnet Fusion txs.
    private val createDiscriminator = hex("181ec828051c0777")
    private val fillDiscriminator = hex("a860b7a35c0a28a0")

    private fun account(n: Int) = "Acc$n"

    // Account order of the program's `Create` struct: src_mint at 2, dst_mint at 7 (12 accounts).
    private fun createAccounts(src: String, dst: String) =
        List(12) { account(it) }.toMutableList().also { it[2] = src; it[7] = dst }

    // Account order of the program's `Fill` struct: src_mint at 4, dst_mint at 5 (17 accounts).
    private fun fillAccounts(src: String, dst: String) =
        List(17) { account(it) }.toMutableList().also { it[4] = src; it[5] = dst }

    @Test
    fun `create names the pair from its src_mint and dst_mint accounts`() {
        val instruction = InvokedInstruction(KnownPrograms.oneInchFusion, createAccounts(wsol, usdc), createDiscriminator + byteArrayOf(1, 2, 3))
        assertEquals(OneInchFusionProgram.SwapMints(wsol, usdc), OneInchFusionProgram.swapMints(instruction))
    }

    @Test
    fun `fill names the pair from its src_mint and dst_mint accounts`() {
        val instruction = InvokedInstruction(KnownPrograms.oneInchFusion, fillAccounts(usdc, wsol), fillDiscriminator)
        assertEquals(OneInchFusionProgram.SwapMints(usdc, wsol), OneInchFusionProgram.swapMints(instruction))
    }

    @Test
    fun `other Fusion instructions such as cancel name no pair`() {
        val cancelDiscriminator = hex("e8dbdf29dbecdcbe")
        val instruction = InvokedInstruction(KnownPrograms.oneInchFusion, List(6) { account(it) }, cancelDiscriminator)
        assertNull(OneInchFusionProgram.swapMints(instruction))
    }

    @Test
    fun `an instruction of another program names no pair even with a matching payload`() {
        val instruction = InvokedInstruction(KnownPrograms.jupiterV6, createAccounts(wsol, usdc), createDiscriminator)
        assertNull(OneInchFusionProgram.swapMints(instruction))
    }

    @Test
    fun `a truncated account list is not decoded`() {
        val instruction = InvokedInstruction(KnownPrograms.oneInchFusion, createAccounts(wsol, usdc).take(8), createDiscriminator)
        assertNull(OneInchFusionProgram.swapMints(instruction))
    }

    @Test
    fun `a payload shorter than a discriminator is not decoded`() {
        val instruction = InvokedInstruction(KnownPrograms.oneInchFusion, createAccounts(wsol, usdc), byteArrayOf(0x18, 0x1e))
        assertNull(OneInchFusionProgram.swapMints(instruction))
    }

    @Test
    fun `the first pair-naming instruction of a transaction wins`() {
        val computeBudget = InvokedInstruction("ComputeBudget111111111111111111111111111111", emptyList(), byteArrayOf(2))
        val create = InvokedInstruction(KnownPrograms.oneInchFusion, createAccounts(wsol, usdc), createDiscriminator)
        assertEquals(OneInchFusionProgram.SwapMints(wsol, usdc), OneInchFusionProgram.swapMints(listOf(computeBudget, create)))
        assertNull(OneInchFusionProgram.swapMints(listOf(computeBudget)))
        assertNull(OneInchFusionProgram.swapMints(emptyList()))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
