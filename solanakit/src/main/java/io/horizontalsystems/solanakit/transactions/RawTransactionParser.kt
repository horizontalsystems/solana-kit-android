package io.horizontalsystems.solanakit.transactions

import org.sol4k.Base58

/**
 * Minimal reader of a serialized Solana transaction (legacy or V0), extracting only what
 * `SolanaKit.sendRawTransaction` needs and sol4k does not expose publicly (its
 * `TransactionMessage.accounts`/`instructions` are internal): the signature count, the embedded
 * blockhash, and each top-level instruction — the program id it INVOKES, its accounts and its
 * data. Program ids are always static account keys (the runtime forbids loading programs from
 * lookup tables), so V0 lookup tables — everything after the instruction list — never need to be
 * parsed; an instruction ACCOUNT that lives in a lookup table (index past the static keys) cannot
 * be resolved offline, and such an instruction is left out of [ParsedTransaction.instructions]
 * (its program id is still reported).
 */
internal object RawTransactionParser {

    data class ParsedTransaction(
        val signatureCount: Int,
        val requiredSignatures: Int,
        /** Base58 blockhash embedded in the message. */
        val recentBlockhash: String,
        /** Base58 program id invoked by each top-level instruction, in order. */
        val invokedProgramIds: List<String>,
        /** Top-level instructions whose accounts are all static keys (see class doc), in order. */
        val instructions: List<InvokedInstruction>,
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
        val instructions = mutableListOf<InvokedInstruction>()
        repeat(instructionCount) {
            val programIdIndex = readByte()
            val instructionAccountCount = readLength()
            val accountIndexes = List(instructionAccountCount) { readByte() }
            val dataLength = readLength()
            val data = rawTransaction.copyOfRange(offset, offset + dataLength)
            offset += dataLength

            val programId = accountKeys.getOrNull(programIdIndex)?.let { Base58.encode(it) } ?: return@repeat
            invokedProgramIds.add(programId)

            val accounts = accountIndexes.map { accountKeys.getOrNull(it) ?: return@repeat }
            instructions.add(InvokedInstruction(programId, accounts.map { Base58.encode(it) }, data))
        }

        return ParsedTransaction(signatureCount, requiredSignatures, recentBlockhash, invokedProgramIds, instructions)
    }
}
