package ru.lobotino.walktraveller.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.lobotino.walktraveller.database.dao.PathDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.PathPointRelation
import ru.lobotino.walktraveller.database.model.Point

@Database(entities = [Point::class, PathPointRelation::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPointsDao(): PointsDao
    abstract fun getPathsDao(): PathDao
}