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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE paths ADD COLUMN distance FLOAT DEFAULT 0 NOT NULL")
    }
}

@Database(
    entities = [EntityPoint::class, EntityPath::class, EntityPathPointRelation::class, EntityPathSegment::class],
    version = 2
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
        AppDatabase::class.java, App.PATH_DATABASE_NAME
    ).addMigrations(MIGRATION_1_2).build()
}