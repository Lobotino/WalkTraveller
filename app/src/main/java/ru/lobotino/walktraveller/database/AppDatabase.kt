package ru.lobotino.walktraveller.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.lobotino.walktraveller.database.dao.PathPointsRelationsDao
import ru.lobotino.walktraveller.database.dao.PathSegmentRelationsDao
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.Path
import ru.lobotino.walktraveller.database.model.PathPointRelation
import ru.lobotino.walktraveller.database.model.PathSegmentRelation
import ru.lobotino.walktraveller.database.model.Point

@Database(
    entities = [Point::class, Path::class, PathPointRelation::class, PathSegmentRelation::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPathSegmentRelationsDao(): PathSegmentRelationsDao
    abstract fun getPathsDao(): PathsDao
    abstract fun getPointsDao(): PointsDao
    abstract fun getPathPointsRelationsDao(): PathPointsRelationsDao
}