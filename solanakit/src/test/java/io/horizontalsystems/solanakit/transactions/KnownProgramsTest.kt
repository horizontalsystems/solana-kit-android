package io.horizontalsystems.solanakit.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownProgramsTest {

    private val systemProgram = "11111111111111111111111111111111"
    private val tokenProgram = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    @Test
    fun `createsTokenAccount is true when the associated-token program was invoked`() {
        val invoked = listOf(KnownPrograms.associatedTokenAccount, tokenProgram)
        assertTrue(KnownPrograms.createsTokenAccount(invoked))
    }

    @Test
    fun `createsTokenAccount is false for a plain transfer`() {
        val invoked = listOf(systemProgram, tokenProgram)
        assertFalse(KnownPrograms.createsTokenAccount(invoked))
    }

    @Test
    fun `createsTokenAccount is false for no instructions`() {
        assertFalse(KnownPrograms.createsTokenAccount(emptyList()))
    }

    @Test
    fun `associated-token program is not surfaced as a recognized programId`() {
        // It marks rent, not a swap, so it must never land on Transaction.programIds.
        assertFalse(KnownPrograms.associatedTokenAccount in KnownPrograms.all)
    }
}
