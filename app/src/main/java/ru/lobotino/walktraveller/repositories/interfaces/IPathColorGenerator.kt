package ru.lobotino.walktraveller.repositories.interfaces

interface IPathColorGenerator {

    fun getColorForPath(pathId: Long): String

}