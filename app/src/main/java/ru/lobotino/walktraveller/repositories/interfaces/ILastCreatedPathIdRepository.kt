package ru.lobotino.walktraveller.repositories.interfaces

interface ILastCreatedPathIdRepository {

    fun getLastCreatedPathId(): Long?

    fun setLastCreatedPathId(pathId: Long)
}
