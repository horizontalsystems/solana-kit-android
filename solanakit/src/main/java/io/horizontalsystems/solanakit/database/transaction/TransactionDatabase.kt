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
    version = 11,
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

        // Existing rows stay NULL ("no pair known"); a Fusion row synced before this was tracked is
        // re-tagged the next time its confirmed record is fetched (TransactionManager keeps the
        // freshly derived value).
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Transaction` ADD COLUMN `swapSrcMint` TEXT")
                db.execSQL("ALTER TABLE `Transaction` ADD COLUMN `swapDstMint` TEXT")
            }
        }

        fun getInstance(context: Context, databaseName: String): TransactionDatabase {
            return Room.databaseBuilder(context, TransactionDatabase::class.java, databaseName)
//                .setQueryCallback({ sqlQuery, bindArgs ->
//                    println("SQL Query: $sqlQuery SQL Args: $bindArgs")
//                }, Executors.newSingleThreadExecutor())
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
        }

    }

}
