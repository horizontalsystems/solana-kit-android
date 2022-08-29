package io.horizontalsystems.solanakit.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.horizontalsystems.solanakit.database.dao.BalanceDao
import io.horizontalsystems.solanakit.database.dao.LastBlockHeightDao
import io.horizontalsystems.solanakit.models.BalanceEntity
import io.horizontalsystems.solanakit.models.LastBlockHeightEntity

@Database(entities = [BalanceEntity::class, LastBlockHeightEntity::class], version = 1, exportSchema = false)
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
