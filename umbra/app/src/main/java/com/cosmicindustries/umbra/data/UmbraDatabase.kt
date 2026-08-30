package com.cosmicindustries.umbra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cosmicindustries.umbra.firewall.AppMode
import com.cosmicindustries.umbra.firewall.AppRule
import com.cosmicindustries.umbra.firewall.AppRuleDao
import com.cosmicindustries.umbra.logging.ConnectionEvent
import com.cosmicindustries.umbra.logging.ConnectionEventDao
import com.cosmicindustries.umbra.logging.TrafficEngine

class Converters {
    @TypeConverter
    fun appModeToString(mode: AppMode): String = mode.name

    @TypeConverter
    fun stringToAppMode(value: String): AppMode = AppMode.valueOf(value)

    @TypeConverter
    fun engineToString(engine: TrafficEngine): String = engine.name

    @TypeConverter
    fun stringToEngine(value: String): TrafficEngine = TrafficEngine.valueOf(value)
}

@Database(
    entities = [AppRule::class, ConnectionEvent::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UmbraDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun connectionEventDao(): ConnectionEventDao

    companion object {
        @Volatile private var instance: UmbraDatabase? = null

        fun get(context: Context): UmbraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                UmbraDatabase::class.java,
                "umbra.db",
            )
                // Version 2 dropped AppMode.DPI_BYPASS (see AppRule.kt) and
                // TrafficEngine.DPI_BYPASS (see ConnectionEvent.kt); a real
                // migration isn't worth writing pre-release for what's
                // still an unreleased, locally-generated debug database —
                // destructively rebuild instead of crashing on the old
                // enum value.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
