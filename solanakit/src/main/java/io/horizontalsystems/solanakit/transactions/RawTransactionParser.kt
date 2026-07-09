package io.horizontalsystems.solanakit.transactions

import org.sol4k.Base58

/**
 * Minimal reader of a serialized Solana transaction (legacy or V0), extracting only what
 * `SolanaKit.sendRawTransaction` needs and sol4k does not expose publicly (its
 * `TransactionMessage.accounts`/`instructions` are internal): the signature count, the embedded
 * blockhash, and the program id INVOKED by each top-level instruction. Program ids are always
 * static account keys (the runtime forbids loading programs from lookup tables), so V0 lookup
 * tables — everything after the instruction list — never need to be parsed.
 */
internal object RawTransactionParser {

    data class ParsedTransaction(
        val signatureCount: Int,
        val requiredSignatures: Int,
        /** Base58 blockhash embedded in the message. */
        val recentBlockhash: String,
        /** Base58 program id invoked by each top-level instruction, in order. */
        val invokedProgramIds: List<String>,
    )

    fun parse(rawTransaction: ByteArray): ParsedTransaction {
        var offset = 0

        fun readByte(): Int = rawTransaction[offset++].toInt() and 0xFF

        // compact-u16 ("shortvec"): 7 bits per byte, high bit = continuation
        fun readLength(): Int {
            var length = 0
            var shift = 0
            while (true) {
                val byte = readByte()
                length = length or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) return length
                shift += 7
            }
        }

        val signatureCount = readLength()
        offset += signatureCount * 64

        // V0 messages are marked by the high bit of the first message byte; legacy has none.
        if (rawTransaction[offset].toInt() and 0x80 != 0) {
            offset++
        }

        val requiredSignatures = readByte()
        offset += 2 // numReadonlySignedAccounts, numReadonlyUnsignedAccounts

        val accountCount = readLength()
        val accountKeys = List(accountCount) {
            val key = rawTransaction.copyOfRange(offset, offset + 32)
            offset += 32
            key
        }

        val recentBlockhash = Base58.encode(rawTransaction.copyOfRange(offset, offset + 32))
        offset += 32

        val instructionCount = readLength()
        val invokedProgramIds = mutableListOf<String>()
        repeat(instructionCount) {
            val programIdIndex = readByte()
            val instructionAccountCount = readLength()
            offset += instructionAccountCount
            val dataLength = readLength()
            offset += dataLength

            accountKeys.getOrNull(programIdIndex)?.let {
                invokedProgramIds.add(Base58.encode(it))
            }
        }

        return ParsedTransaction(signatureCount, requiredSignatures, recentBlockhash, invokedProgramIds)
    }
}
