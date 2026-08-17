package io.horizontalsystems.solanakit.transactions

import org.sol4k.Connection
import org.sol4k.Constants.TOKEN_2022_PROGRAM_ID
import org.sol4k.Keypair
import org.sol4k.PublicKey
import org.sol4k.Transaction
import org.sol4k.api.Commitment
import org.sol4k.instruction.CreateAssociatedToken2022AccountInstruction
import org.sol4k.instruction.Instruction
import org.sol4k.instruction.SetComputeUnitLimitInstruction
import org.sol4k.instruction.SetComputeUnitPriceInstruction
import org.sol4k.instruction.Token2022TransferInstruction
import java.util.Base64

/**
 * Builds, signs and submits an SPL Token-2022 (Token Extensions) transfer via sol4k.
 *
 * The classic com.solana send path ([Extensions.sendSPLTokens]) is hardcoded to the classic SPL
 * Token program and cannot transfer Token-2022 mints: the associated token account is derived with
 * the wrong program id in the PDA seeds, and fee-bearing mints (e.g. Pump.fun tokens) reject the
 * plain `Transfer` instruction. This path derives the ATAs with `TOKEN_2022_PROGRAM_ID` and uses
 * `TransferChecked` (which Token-2022 requires, and which carries the mint + decimals).
 *
 * All calls are blocking (sol4k uses HttpURLConnection); invoke from a background dispatcher.
 */
class Token2022Sender(private val rpcUrl: String) {

    data class Result(
        val transactionHash: String,
        val base64Encoded: String,
        val blockhash: String,
        val lastValidBlockHeight: Long,
    )

    fun send(
        mint: PublicKey,
        walletAddress: PublicKey,
        recipient: PublicKey,
        amount: Long,
        decimals: Int,
        keypair: Keypair,
    ): Result {
        val connection = Connection(rpcUrl)

        // ATA derivation MUST use the Token-2022 program id in the seeds.
        val source = PublicKey.findProgramDerivedAddress(walletAddress, mint, TOKEN_2022_PROGRAM_ID).publicKey
        val destination = PublicKey.findProgramDerivedAddress(recipient, mint, TOKEN_2022_PROGRAM_ID).publicKey

        val instructions = mutableListOf<Instruction>(
            SetComputeUnitLimitInstruction(COMPUTE_UNIT_LIMIT),
            SetComputeUnitPriceInstruction(COMPUTE_UNIT_PRICE),
        )

        // Create the recipient's associated token account if it does not exist yet.
        if (connection.getAccountInfo(destination) == null) {
            instructions.add(
                CreateAssociatedToken2022AccountInstruction(
                    payer = walletAddress,
                    associatedToken = destination,
                    owner = recipient,
                    mint = mint,
                )
            )
        }

        instructions.add(
            Token2022TransferInstruction(
                from = source,
                to = destination,
                mint = mint,
                owner = walletAddress,
                amount = amount,
                decimals = decimals,
            )
        )

        val blockhashInfo = connection.getLatestBlockhashExtended(Commitment.FINALIZED)
        val transaction = Transaction(blockhashInfo.blockhash, instructions, walletAddress)
        transaction.sign(keypair)

        val base64Encoded = Base64.getEncoder().encodeToString(transaction.serialize())
        val transactionHash = connection.sendTransaction(transaction)

        return Result(transactionHash, base64Encoded, blockhashInfo.blockhash, blockhashInfo.lastValidBlockHeight)
    }

    companion object {
        // Mirror TransactionManager.priorityFeeInstructions().
        private const val COMPUTE_UNIT_LIMIT = 300_000L
        private const val COMPUTE_UNIT_PRICE = 500_000L
    }
}
