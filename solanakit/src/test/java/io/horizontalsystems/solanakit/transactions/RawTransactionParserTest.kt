package io.horizontalsystems.solanakit.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class RawTransactionParserTest {

    // Mainnet Jupiter v6 swap, V0 message (with address lookup tables):
    // Yvc2AmmmMwn6FdTC3dFqDycTRFQgH7k7DDsQJ86UBtCqDCkb6aYCPnFQNJxY73v8gG7NncN36QGbRRHfFRiYY8t
    private val jupiterV0Tx =
        "ARuHvJNIL3Hufj/Jc0F2YK0kr/cY4/SfKn/QkYoKKuyi4L08LKUI0YwiECaCjgw2Y/2e5gC+Y0eEPcyqufPsyQ2AAQAECPSSlhqepX6/HrSS46MZovr/QNtZ9HF2x2ttHRJ0cznBM3emP5VTGuvhVAtCeCFtGVgsDuHOMh6LF14zatClMW1jPVMTQqan3xZwEdYvX58aLu6Xdy1fU3GBOPaSqvHHUMtqa1FW+FdtqQgM7bdPDCFOZ5j9Uk7MU5Q5jdUawfWwAwZGb+UhFzL/7K26csOb57yM5bvF9xJrLEObOkAAAAAEedVb8jHAbu50xW7OaBUH/bGy3qP0jlECsc2iVrwTj7Q/+if11/ZKdMCbHylYed5LCas238ndUUsyGqezjOXo+b7SB/D/xQH0I0yKJWMQqWtTyG+Uc19GnNIpb6dTvEe6rZDrB3d48x0OqLR+nR+Huz2kG440vpEuYzUGISDF0wMEAAUCxB4BAAQACQP/yBQAAAAAAAUYAAIBDw0MDAUGBQMOAAgKCQECDAwLEAUHKLtk+swxxK8UV3q0AwAAAAAl6jUAAAAAAGQAZAAAAAEAAACNABAnAAEBrt8BFUgV9dJh9BWPGETzkUh+QxeZ4SQ+ttVPXt2PgJEDVldPBlADU1IJVA=="

    // Mainnet vote transaction, legacy message:
    // 5BJHwpVVUe7QvEWPqd9sRYBfDAmcnQktZuxxF2wUL8EfqqioiC89tuDfx9g6QViChwKskcKq4yrdiHZEAXxAEyEz
    private val voteLegacyTx =
        "AdDy30G1yg1+wTQIzNT0Dc5BbabZcAh0iYIatn6wB7okOFhPl3RTzvucRkW39NOs8wc5vKHNyYoEbE8wqQpnIQMBAAEDFh1Q5EoE6hsz33ShzoO6H2XMw0J1VOpVk5rnVJovlWDIQWnKylvMxQTQuqMaTCl13d9yJOiySGGnglI/oM1gggdhSB01dHS7fE12JOvTvbPYNV5z0RBD/A2jU4AAAAAAX9ykmCtU6gUDFJwgP5MQnXvBR7/OLSfGoIZ1laswmL4BAgIBAJQBDgAAAP2hvBkAAAAAHwEfAR4BHQEcARsBGgEZARgBFwEWARUBFAETARIBEQEQAQ8BDgENAQwBCwEKAQkBCAEHAQYBBQEEAQMBAgEBzjhxYNQYHviIUm8mIjBQvEUvtsseDh42M41x1niNW/YBmYdPagAAAADm1RH7hxTjZLqQcKxeFQSa9YEfYj1poVOirkPjzkO2Yg=="

    // Mainnet 1inch Fusion order-create (SOL -> SPL token), legacy message:
    // 3FAYz8L8baCcxvaHkQwXmwi9eoeQAq1XEp39hhnMpN7NDBiMbgCqEGB65qbAmKgJP6ffpu2TLs2XMamZ1Mz7ujm
    private val fusionCreateLegacyTx =
        "AQHvcbfv6i4cUBuc23P8E7EouVOZpUZ6UBrPgkt2XJI2kygef/FuzCX+3gpoLpNZQ1MRSTEvMWzQj8+JdkAkkQABAAgLU4M+PYXkd1tMjXHiYpZBpXoATlHXUgR5TT5GK0gSYlM/tLF/XIhh4fEtFQqqbM66vLVgfurQslwpBf2txFrS+ZE+THemEbTx6E76aOb6xC4xsXWEzopR+cYT2CEvZ8b6AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABE5C2KhLBIdBjGxIPxYDkCtPii8ze92z1FeDyCq4ZDP4yXJY9OJInxuz0QKRSODYMLWhOZ2v8QhASOe9jb6fhZAwZGb+UhFzL/7K26csOb57yM5bvF9xJrLEObOkAAAAC7RKdYDUhyG3Is6GSwx9bIZENh5BVWIqLPGPTo2CM7x/NCeCjWMfnSut9INXSlZcp8u5HypHzrkvgONXdNfRo+BpuIV/6rgYT7aH9jRhjANdrEOdwa6ztVmKDwAAAAAAEG3fbh12Whk9nL4UbO63msHLSF7V9bN5E6jPWFfv8AqS7n6G9kQbRRTkMl2VR2J2n1Y2GyFNKpXbXXiECbtAHbBAYABQJYkgEABgAJA+gDAAAAAAAABQYAAgAJAwoACAwDBwkKAQAIBAAFCAhNGB7IKAUcB3fceD0FAJQ1dwAAAABu8PKECAAAAG7w8oQIAAAAImuiagEAAAAAAAABAAAAAAAAAGJqomq0AAAA+AEBAAAAigB4AAEAAAA="

    @Test
    fun parseV0JupiterSwap() {
        val parsed = RawTransactionParser.parse(Base64.getDecoder().decode(jupiterV0Tx))

        assertEquals(1, parsed.signatureCount)
        assertEquals(1, parsed.requiredSignatures)
        assertEquals("DZiLQHwp8FzMvFicUXgVziiT6JHinNR1D3Mvn72tgnaW", parsed.recentBlockhash)
        assertEquals(
            listOf(
                "ComputeBudget111111111111111111111111111111",
                "ComputeBudget111111111111111111111111111111",
                "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
            ),
            parsed.invokedProgramIds
        )
        assertEquals(KnownPrograms.jupiterV6, KnownPrograms.recognized(parsed.invokedProgramIds))
    }

    @Test
    fun parseLegacyVoteTransaction() {
        val parsed = RawTransactionParser.parse(Base64.getDecoder().decode(voteLegacyTx))

        assertEquals(1, parsed.signatureCount)
        assertEquals(1, parsed.requiredSignatures)
        assertEquals("7TCsQQoyf35PhjhPHFbeAyYB2Noznn5YGJr3KvwVdR9o", parsed.recentBlockhash)
        assertEquals(listOf("Vote111111111111111111111111111111111111111"), parsed.invokedProgramIds)
        assertNull(KnownPrograms.recognized(parsed.invokedProgramIds))
    }

    @Test
    fun parseLegacyFusionOrderCreateNamesTheSwappedPair() {
        val parsed = RawTransactionParser.parse(Base64.getDecoder().decode(fusionCreateLegacyTx))

        assertEquals(1, parsed.requiredSignatures)
        assertEquals("4A6ra65XXU3hYDDShS9Lf4PphsNW8pRgrsRVwLQzqWjL", parsed.recentBlockhash)
        assertEquals(
            listOf(
                "ComputeBudget111111111111111111111111111111",
                "ComputeBudget111111111111111111111111111111",
                KnownPrograms.associatedTokenAccount,
                KnownPrograms.oneInchFusion,
            ),
            parsed.invokedProgramIds
        )
        // Legacy message: every instruction account is a static key, so all four are decoded.
        assertEquals(4, parsed.instructions.size)
        val create = parsed.instructions.last()
        assertEquals(KnownPrograms.oneInchFusion, create.programId)
        assertEquals(12, create.accounts.size)

        assertEquals(
            OneInchFusionProgram.SwapMints(
                srcMint = "So11111111111111111111111111111111111111112",
                dstMint = "5dvXTZ5qwgafnHtwu3Ls3QrWx1U4LQsFeCuJgkk4QEC6"
            ),
            OneInchFusionProgram.swapMints(parsed.instructions)
        )
    }

    @Test
    fun parseV0JupiterSwapNamesNoPair() {
        // Jupiter moves both sides in one transaction; nothing to decode, and lookup-table accounts
        // (the swap's account indexes exceed the 8 static keys) must not break parsing.
        val parsed = RawTransactionParser.parse(Base64.getDecoder().decode(jupiterV0Tx))
        assertNull(OneInchFusionProgram.swapMints(parsed.instructions))
        assertEquals(3, parsed.invokedProgramIds.size)
    }

    @Test
    fun recognizedDeduplicatesAndIgnoresUnknown() {
        assertEquals(
            KnownPrograms.jupiterV6,
            KnownPrograms.recognized(
                listOf("ComputeBudget111111111111111111111111111111", KnownPrograms.jupiterV6, KnownPrograms.jupiterV6)
            )
        )
        assertNull(KnownPrograms.recognized(emptyList()))
    }
}
