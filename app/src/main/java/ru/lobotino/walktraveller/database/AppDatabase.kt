package ru.lobotino.walktraveller.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.lobotino.walktraveller.database.dao.PathPointsRelationsDao
import ru.lobotino.walktraveller.database.dao.PathSegmentRelationsDao
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint

@Database(
    entities = [EntityPoint::class, EntityPath::class, EntityPathPointRelation::class, EntityPathSegment::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPathSegmentRelationsDao(): PathSegmentRelationsDao
    abstract fun getPathsDao(): PathsDao
    abstract fun getPointsDao(): PointsDao
    abstract fun getPathPointsRelationsDao(): PathPointsRelationsDao
}