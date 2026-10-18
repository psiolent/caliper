package psiolent.caliper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntity::class,
        RecipeEntity::class,
        BatchEntity::class,
        MealEntity::class,
        LogEntryEntity::class,
        ProfileEntity::class,
        SessionEntity::class,
        MessageEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CaliperDatabase : RoomDatabase() {

    abstract fun foods(): FoodDao
    abstract fun recipes(): RecipeDao
    abstract fun batches(): BatchDao
    abstract fun meals(): MealDao
    abstract fun logs(): LogDao
    abstract fun profile(): ProfileDao
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao

    companion object {
        /**
         * Meals became real: the agent creates one and items point at it, in place
         * of the old optional label on each entry.
         *
         * Entries logged before this cannot be assigned to a meal (nothing recorded
         * what grouped them), and the app's data was wiped alongside this change, so
         * the table is rebuilt empty rather than grouped by a guess.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meals` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("DROP TABLE IF EXISTS `log_entries`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_entries` (`id` TEXT NOT NULL, `mealId` TEXT NOT NULL, " +
                        "`targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, `unit` TEXT NOT NULL, `grams` REAL NOT NULL, " +
                        "`nutrients` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * An item stopped carrying its own time: it belongs to a meal, and the meal
         * says when. Rebuild the table without it; entries are re-logged rather than
         * guessed at.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `log_entries`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_entries` (`id` TEXT NOT NULL, `mealId` TEXT NOT NULL, " +
                        "`targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, `unit` TEXT NOT NULL, `grams` REAL NOT NULL, " +
                        "`nutrients` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun open(context: Context): CaliperDatabase =
            Room.databaseBuilder(context.applicationContext, CaliperDatabase::class.java, "caliper.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
