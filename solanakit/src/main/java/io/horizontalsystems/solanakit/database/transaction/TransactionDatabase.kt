package io.horizontalsystems.solanakit.database.transaction

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.horizontalsystems.solanakit.database.transaction.dao.MintAccountDao
import io.horizontalsystems.solanakit.database.transaction.dao.TransactionSyncerStateDao
import io.horizontalsystems.solanakit.database.transaction.dao.TransactionsDao
import io.horizontalsystems.solanakit.models.*
import java.util.concurrent.Executors

@Database(
    entities = [
        SyncedBlockTime::class,
        MintAccount::class,
        TokenTransfer::class,
        Transaction::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class TransactionDatabase : RoomDatabase() {

    abstract fun transactionSyncerStateDao(): TransactionSyncerStateDao
    abstract fun transactionsDao(): TransactionsDao
    abstract fun mintAccountDao(): MintAccountDao

    companion object {

        fun getInstance(context: Context, databaseName: String): TransactionDatabase {
            return Room.databaseBuilder(context, TransactionDatabase::class.java, databaseName)
//                .setQueryCallback({ sqlQuery, bindArgs ->
//                    println("SQL Query: $sqlQuery SQL Args: $bindArgs")
//                }, Executors.newSingleThreadExecutor())
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
        }

    }

}
