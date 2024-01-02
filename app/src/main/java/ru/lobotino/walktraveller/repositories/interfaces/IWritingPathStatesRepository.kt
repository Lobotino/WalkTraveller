package ru.lobotino.walktraveller.repositories.interfaces

interface IWritingPathStatesRepository {

    fun setWritingPathNow(writingPathNow: Boolean)

    fun isWritingPathNow(): Boolean
}
