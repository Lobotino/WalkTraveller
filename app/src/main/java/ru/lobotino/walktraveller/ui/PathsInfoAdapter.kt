package ru.lobotino.walktraveller.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.map.MapPathInfo
import java.text.SimpleDateFormat
import java.util.*


class PathsInfoAdapter : RecyclerView.Adapter<PathsInfoAdapter.PathInfoItem>() {

    private var pathsItems: List<MapPathInfo> = ArrayList<MapPathInfo>()

    @SuppressLint("NotifyDataSetChanged")
    fun setPathsInfoItems(pathInfoItems: List<MapPathInfo>) {
        pathsItems = pathInfoItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathInfoItem {
        return PathInfoItem(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.path_info_item,
                    parent,
                    false
                )
        )
    }

    override fun onBindViewHolder(holder: PathInfoItem, position: Int) {
        holder.bind(pathsItems[position])
    }

    override fun getItemCount(): Int {
        return pathsItems.size
    }

    class PathInfoItem(view: View) : RecyclerView.ViewHolder(view) {

        companion object {
            private const val DATE_FORMAT = "dd/MM/yyyy"
            private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH)
        }

        private val pathLength: TextView
        private val pathDate: TextView
        private val pathColor: CardView

        init {
            pathLength = view.findViewById(R.id.path_length)
            pathDate = view.findViewById(R.id.path_date)
            pathColor = view.findViewById(R.id.path_color)
        }

        fun bind(pathInfo: MapPathInfo) {
            pathColor.setCardBackgroundColor(Color.parseColor(pathInfo.color))
            pathDate.text = formatTimestampDate(pathInfo.timestamp)
            pathLength.text = "13km" //TODO calculate path length
        }

        private fun formatTimestampDate(timestamp: Long): String {
            return dateFormat.format(Date(timestamp))
        }
    }
}
