package io.horizontalsystems.solanakit.transactions

import com.solana.actions.Action
import com.solana.actions.findSPLTokenDestinationAddress
import com.solana.actions.serializeAndSendWithFee
import com.solana.core.Account
import com.solana.core.PublicKey
import com.solana.core.Transaction
import com.solana.core.TransactionInstruction
import com.solana.programs.AssociatedTokenProgram
import com.solana.programs.SystemProgram
import com.solana.programs.TokenProgram
import com.solana.vendor.ContResult
import com.solana.vendor.ResultError
import com.solana.vendor.flatMap

fun Action.sendSOL(
    account: Account,
    destination: PublicKey,
    amount: Long,
    instructions: List<TransactionInstruction>,
    onComplete: ((Result<String>) -> Unit)
) {
    val transferInstruction = SystemProgram.transfer(account.publicKey, destination, amount)
    val transaction = Transaction()

    if (instructions.isNotEmpty()) {
        transaction.add(*instructions.toTypedArray())
    }

    transaction.add(transferInstruction)

    this.serializeAndSendWithFee(transaction, listOf(account), null) { result ->
        result.onSuccess {
            onComplete(Result.success(it))
        }.onFailure {
            onComplete(Result.failure(it))
        }
    }
}

fun Action.sendSPLTokens(
    mintAddress: PublicKey,
    fromPublicKey: PublicKey,
    destinationAddress: PublicKey,
    amount: Long,
    allowUnfundedRecipient: Boolean = false,
    account: Account,
    instructions: List<TransactionInstruction>,
    onComplete: ((com.solana.vendor.Result<String, ResultError>) -> Unit)
) {
    ContResult { cb ->
        this.findSPLTokenDestinationAddress(
            mintAddress,
            destinationAddress,
            allowUnfundedRecipient
        ) { cb(it) }
    }.flatMap { spl ->
        val toPublicKey = spl.first
        val unregisteredAssociatedToken = spl.second
        if (fromPublicKey.toBase58() == toPublicKey.toBase58()) {
            return@flatMap ContResult.failure(ResultError("Same send and destination address."))
        }
        val transaction = Transaction()

        if (instructions.isNotEmpty()) {
            transaction.add(*instructions.toTypedArray())
        }

        // create associated token address
        if (unregisteredAssociatedToken) {
            val mint = mintAddress
            val owner = destinationAddress
            val createATokenInstruction = AssociatedTokenProgram.createAssociatedTokenAccountInstruction(
                mint = mint,
                associatedAccount = toPublicKey,
                owner = owner,
                payer = account.publicKey
            )
            transaction.add(createATokenInstruction)
        }

        // send instruction
        val sendInstruction = TokenProgram.transfer(fromPublicKey, toPublicKey, amount, account.publicKey)
        transaction.add(sendInstruction)
        return@flatMap ContResult.success(transaction)
    }.flatMap { transaction ->
        return@flatMap ContResult<String, ResultError> { cb ->
            this.serializeAndSendWithFee(transaction, listOf(account)) { result ->
                result.onSuccess {
                    cb(com.solana.vendor.Result.success(it))
                }.onFailure {
                    cb(com.solana.vendor.Result.failure(ResultError(it)))
                }
            }
        }
    }.run(onComplete)
}
