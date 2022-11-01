package io.horizontalsystems.solanakit.noderpc

import com.metaplex.lib.drivers.solana.AccountInfo
import com.metaplex.lib.drivers.solana.Connection
import com.solana.core.PublicKey
import com.solana.vendor.borshj.BorshCodable
import io.horizontalsystems.solanakit.noderpc.operationhandlers.FindMetadataAccountListOperationHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class NftClient(private val connection: Connection,
                private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    suspend fun findAllByMintList(mintKeys: List<PublicKey>): Result<List<AccountInfo<BorshCodable?>?>> =
        FindMetadataAccountListOperationHandler(connection, dispatcher).handle(mintKeys)

}