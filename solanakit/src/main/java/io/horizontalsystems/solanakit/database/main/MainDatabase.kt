package io.horizontalsystems.solanakit.database.main

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.horizontalsystems.solanakit.database.main.dao.BalanceDao
import io.horizontalsystems.solanakit.database.main.dao.LastBlockHeightDao
import io.horizontalsystems.solanakit.database.transaction.RoomTypeConverters
import io.horizontalsystems.solanakit.models.BalanceEntity
import io.horizontalsystems.solanakit.models.LastBlockHeightEntity

@Database(
    entities = [
        BalanceEntity::class,
        LastBlockHeightEntity::class
    ],
    version = 4, exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MainDatabase : RoomDatabase() {

    abstract fun balanceDao(): BalanceDao
    abstract fun lastBlockHeightDao(): LastBlockHeightDao

    companion object {

        fun getInstance(context: Context, databaseName: String): MainDatabase {
            return Room.databaseBuilder(context, MainDatabase::class.java, databaseName)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
        }

    }

}
