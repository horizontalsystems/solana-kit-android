package io.horizontalsystems.solanakit.database.transaction

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.horizontalsystems.solanakit.database.transaction.dao.MintAccountDao
import io.horizontalsystems.solanakit.database.transaction.dao.TokenAccountDao
import io.horizontalsystems.solanakit.database.transaction.dao.TransactionSyncerStateDao
import io.horizontalsystems.solanakit.database.transaction.dao.TransactionsDao
import io.horizontalsystems.solanakit.models.*

@Database(
    entities = [
        LastSyncedTransaction::class,
        MintAccount::class,
        TokenTransfer::class,
        Transaction::class,
        TokenAccount::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class TransactionDatabase : RoomDatabase() {

    abstract fun transactionSyncerStateDao(): TransactionSyncerStateDao
    abstract fun transactionsDao(): TransactionsDao
    abstract fun mintAccountDao(): MintAccountDao
    abstract fun tokenAccountsDao(): TokenAccountDao

    companion object {

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Transaction` ADD COLUMN `programIds` TEXT")
            }
        }

        // Nullable INTEGER (Room maps Boolean? to INTEGER): existing rows stay NULL = "unknown",
        // so clients fall back to their amount heuristic for them; new rows get 0/1 at parse time.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Transaction` ADD COLUMN `createdTokenAccount` INTEGER")
            }
        }

        fun getInstance(context: Context, databaseName: String): TransactionDatabase {
            return Room.databaseBuilder(context, TransactionDatabase::class.java, databaseName)
//                .setQueryCallback({ sqlQuery, bindArgs ->
//                    println("SQL Query: $sqlQuery SQL Args: $bindArgs")
//                }, Executors.newSingleThreadExecutor())
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
        }

    }

}
