package io.horizontalsystems.solanakit.transactions

import com.solana.actions.Action
import com.solana.actions.findSPLTokenDestinationAddress
import com.solana.actions.serializeAndSendWithFee
import com.solana.api.Api
import com.solana.api.getBalance
import com.solana.api.getBlockHeight
import com.solana.api.getConfirmedTransaction
import com.solana.api.getMultipleAccounts
import com.solana.core.Account
import com.solana.core.PublicKey
import com.solana.core.Transaction
import com.solana.core.TransactionInstruction
import com.solana.models.ConfirmedTransaction
import com.solana.models.buffer.BufferInfo
import com.solana.programs.AssociatedTokenProgram
import com.solana.programs.SystemProgram
import com.solana.programs.TokenProgram
import com.solana.vendor.ContResult
import com.solana.vendor.ResultError
import com.solana.vendor.flatMap
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges a SolanaKT callback-style API call (`(Result<T>) -> Unit`) to a suspending call.
 */
private suspend fun <T> awaitResult(block: ((Result<T>) -> Unit) -> Unit): T =
    suspendCancellableCoroutine { continuation ->
        block { result ->
            if (!continuation.isActive) return@block
            result
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }
    }

suspend fun Api.getBalance(account: PublicKey): Long =
    awaitResult { getBalance(account, it) }

suspend fun Api.getBlockHeight(): Long =
    awaitResult { getBlockHeight(it) }

suspend fun Api.getConfirmedTransaction(signature: String): ConfirmedTransaction =
    awaitResult { getConfirmedTransaction(signature, it) }

suspend fun <T> Api.getMultipleAccounts(
    accounts: List<PublicKey>,
    decodeTo: Class<T>
): List<BufferInfo<T>?> =
    awaitResult { getMultipleAccounts(accounts, decodeTo, it) }

suspend fun Action.sendSOL(
    account: Account,
    destination: PublicKey,
    amount: Long,
    instructions: List<TransactionInstruction>,
    recentBlockHash: String
): Pair<String, String> {
    val transferInstruction = SystemProgram.transfer(account.publicKey, destination, amount)
    val transaction = Transaction()

    if (instructions.isNotEmpty()) {
        transaction.add(*instructions.toTypedArray())
    }

    transaction.add(transferInstruction)

    val signature = awaitResult<String> { callback ->
        serializeAndSendWithFee(transaction, listOf(account), recentBlockHash, onComplete = callback)
    }
    return Pair(signature, encodeBase64(transaction))
}

suspend fun Action.sendSPLTokens(
    mintAddress: PublicKey,
    fromPublicKey: PublicKey,
    destinationAddress: PublicKey,
    amount: Long,
    allowUnfundedRecipient: Boolean = false,
    account: Account,
    instructions: List<TransactionInstruction>,
    recentBlockHash: String
): Pair<String, String> = suspendCancellableCoroutine { continuation ->
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
        return@flatMap ContResult<Pair<String, String>, ResultError> { cb ->
            this.serializeAndSendWithFee(transaction, listOf(account), recentBlockHash) { result ->
                result.onSuccess {
                    cb(com.solana.vendor.Result.success(Pair(it, encodeBase64(transaction))))
                }.onFailure {
                    cb(com.solana.vendor.Result.failure(ResultError(it)))
                }
            }
        }
    }.run { result ->
        if (!continuation.isActive) return@run
        result.onSuccess {
            continuation.resume(it)
        }.onFailure {
            continuation.resumeWithException(it)
        }
    }
}

private fun encodeBase64(transaction: Transaction): String {
    val serialized = transaction.serialize()
    return Base64.getEncoder().encodeToString(serialized)
}
