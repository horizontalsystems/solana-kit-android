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
