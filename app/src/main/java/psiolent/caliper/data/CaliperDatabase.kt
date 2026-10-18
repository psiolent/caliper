package psiolent.caliper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FoodEntity::class,
        RecipeEntity::class,
        BatchEntity::class,
        LogEntryEntity::class,
        ProfileEntity::class,
        SessionEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CaliperDatabase : RoomDatabase() {

    abstract fun foods(): FoodDao
    abstract fun recipes(): RecipeDao
    abstract fun batches(): BatchDao
    abstract fun logs(): LogDao
    abstract fun profile(): ProfileDao
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao

    companion object {
        fun open(context: Context): CaliperDatabase =
            Room.databaseBuilder(context.applicationContext, CaliperDatabase::class.java, "caliper.db")
                .build()
    }
}
