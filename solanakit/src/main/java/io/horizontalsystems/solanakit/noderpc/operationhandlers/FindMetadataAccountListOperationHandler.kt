package io.horizontalsystems.solanakit.noderpc.operationhandlers

import com.metaplex.lib.drivers.solana.AccountInfo
import com.metaplex.lib.drivers.solana.Connection
import com.metaplex.lib.programs.token_metadata.accounts.MetadataAccount
import com.metaplex.lib.shared.OperationError
import com.metaplex.lib.shared.OperationResult
import com.metaplex.lib.shared.getOrDefault
import com.solana.core.PublicKey
import com.solana.vendor.borshj.BorshCodable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class FindMetadataAccountListOperationHandler(val connection: Connection, val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    private val chunkSize = 100

    suspend fun handle(input: List<PublicKey>): Result<List<AccountInfo<BorshCodable?>?>> =
        Result.success(input.map {
                MetadataAccount.pda(it).getOrDefault(null)
                    ?: return Result.failure(OperationError.CouldNotFindPDA)
            }.chunked(chunkSize).map { chunk ->
                gma(chunk).getOrElse {
                    return Result.failure(OperationError.GmaBuilderError(it))
                }
            }.flatten()
        )

    private suspend fun gma(publicKeys: List<PublicKey>): Result<List<AccountInfo<BorshCodable?>?>> =
        connection.getMultipleAccountsInfo(BorshCodeableSerializer(MetadataAccount::class.java), publicKeys)

}