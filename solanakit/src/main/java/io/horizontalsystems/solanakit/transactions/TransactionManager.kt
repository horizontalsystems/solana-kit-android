package io.horizontalsystems.solanakit.transactions

import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTransaction
import io.reactivex.Single

interface ITransactionsListener {
    fun onTransactionsReceived(transactions: List<FullTransaction>)
}

class TransactionManager(
    val storage: TransactionStorage
) {

    fun getFullTransactionAsync(onlySolTransfers: Boolean, incoming: Boolean?, fromHash: ByteArray?, limit: Int?): Single<List<FullTransaction>> {
//        storage.getFullTransactions()
        return Single.just(listOf())
    }

}
