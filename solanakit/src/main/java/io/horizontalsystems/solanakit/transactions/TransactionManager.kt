package io.horizontalsystems.solanakit.transactions

import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTransaction

class TransactionManager(
    val storage: TransactionStorage
) {

    suspend fun getAllTransaction(incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> =
        storage.getTransactions(incoming, fromHash, limit)

    suspend fun getSolTransaction(incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> =
        storage.getSolTransactions(incoming, fromHash, limit)

    suspend fun getSplTransaction(mintAddress: String, incoming: Boolean?, fromHash: String?, limit: Int?): List<FullTransaction> =
        storage.getSplTransactions(mintAddress, incoming, fromHash, limit)

}
