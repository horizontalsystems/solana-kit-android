package io.horizontalsystems.solanakit.noderpc

import com.metaplex.lib.programs.token_metadata.accounts.MetadataAccount
import com.metaplex.lib.shared.OperationError
import com.metaplex.lib.shared.getOrDefault
import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.BufferInfo
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class NftClient(private val api: Api) {

    private val chunkSize = 100

    suspend fun findAllByMintList(mintKeys: List<PublicKey>): Result<List<BufferInfo<MetadataAccount>?>> =
        Result.success(mintKeys
            .map {
                MetadataAccount.pda(it).getOrDefault(null)
                    ?: return Result.failure(OperationError.CouldNotFindPDA)
            }
            .chunked(chunkSize).map { gma(it) }
            .flatten()
        )

    private suspend fun gma(publicKeys: List<PublicKey>) = suspendCoroutine<List<BufferInfo<MetadataAccount>?>> { continuation ->
        api.getMultipleAccounts(publicKeys, MetadataAccount::class.java) { result ->
            result.onSuccess {
                continuation.resume(it)
            }

            result.onFailure { exception ->
                continuation.resumeWithException(exception)
            }
        }
    }

}