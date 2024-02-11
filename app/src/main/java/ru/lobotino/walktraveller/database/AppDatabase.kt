package ru.lobotino.walktraveller.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.database.dao.PathPointsRelationsDao
import ru.lobotino.walktraveller.database.dao.PathSegmentRelationsDao
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint

/**
 * Added path length value
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE paths ADD COLUMN length FLOAT DEFAULT 0 NOT NULL")
    }
}

/**
 * Added path most common rating value
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Default value 5 because it's ordinal of UNKNOWN state in MostCommonRating model
        db.execSQL("ALTER TABLE paths ADD COLUMN most_common_rating INTEGER DEFAULT 5 NOT NULL")
    }
}

/**
 * Added shared outer paths. Need to divide my path from outer
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE paths ADD COLUMN is_outer_path INTEGER DEFAULT 0 NOT NULL")
    }
}

/**
 * Added path id to paths segments. Need for select optimization
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE path_segments ADD COLUMN id_path INTEGER DEFAULT 0 NOT NULL")
    }
}

/**
 * Added point timestamp column
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE points ADD COLUMN timestamp INTEGER DEFAULT 0 NOT NULL")
        db.execSQL("UPDATE points SET timestamp = id")
    }
}

@Database(
    entities = [EntityPoint::class, EntityPath::class, EntityPathPointRelation::class, EntityPathSegment::class],
    version = 6
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPathSegmentRelationsDao(): PathSegmentRelationsDao
    abstract fun getPathsDao(): PathsDao
    abstract fun getPointsDao(): PointsDao
    abstract fun getPathPointsRelationsDao(): PathPointsRelationsDao
}

fun provideDatabase(applicationContext: Context): AppDatabase {
    return Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java,
        App.PATH_DATABASE_NAME
    ).addMigrations(MIGRATION_1_2)
        .addMigrations(MIGRATION_2_3)
        .addMigrations(MIGRATION_3_4)
        .addMigrations(MIGRATION_4_5)
        .addMigrations(MIGRATION_5_6)
        .build()
}
