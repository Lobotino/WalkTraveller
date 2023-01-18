package ru.lobotino.walktraveller.repositories

import android.content.Context
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.interfaces.IPathColorGenerator

class PathColorGenerator(context: Context) : IPathColorGenerator {

    private val availableColors: List<String>

    init {
        availableColors = context.resources.getStringArray(R.array.paths_preview_colors).toList()
    }

    //TODO change to rate colors?
    override fun getColorForPath(pathId: Long): String {
        var lastPathIdNumber = pathId.toString().last().digitToInt()
        if (lastPathIdNumber == 0) lastPathIdNumber = pathId.toString().first().digitToInt()
        return availableColors[availableColors.size.rem(lastPathIdNumber)]
    }
}