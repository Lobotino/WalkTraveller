package ru.lobotino.walktraveller.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.ui.model.PathInfoItemModel
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import java.text.SimpleDateFormat
import java.util.*


class PathsInfoAdapter(private val itemButtonClickedListener: (Long, PathItemButtonType) -> Unit) :
    RecyclerView.Adapter<PathsInfoAdapter.PathInfoItem>() {

    private var pathsItems: MutableList<PathInfoItemModel> = ArrayList<PathInfoItemModel>()

    @SuppressLint("NotifyDataSetChanged")
    fun setPathsInfoItems(pathInfoItems: List<MapPathInfo>) {
        pathsItems = pathInfoItems.map { PathInfoItemModel(pathInfo = it) }.toMutableList()
        notifyDataSetChanged()
    }

    fun setPathShowState(pathId: Long, pathInfoItemShowButtonState: PathInfoItemShowButtonState) {
        for (index in pathsItems.indices) {
            if (pathsItems[index].pathInfo.pathId == pathId) {
                pathsItems[index].pathInfoItemShowButtonState = pathInfoItemShowButtonState
                notifyItemChanged(index)
                break
            }
        }
    }

    fun setAllPathsShowState(pathInfoItemShowButtonState: PathInfoItemShowButtonState) {
        for (path in pathsItems) {
            path.pathInfoItemShowButtonState = pathInfoItemShowButtonState
        }
        notifyItemRangeChanged(0, pathsItems.size)
    }

    fun deletePathInfoItem(pathId: Long) {
        for (index in pathsItems.indices) {
            if (pathsItems[index].pathInfo.pathId == pathId) {
                pathsItems.removeAt(index)
                notifyItemRemoved(index)
                break
            }
        }
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
        holder.bind(pathsItems[position], itemButtonClickedListener)
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
        private val pathButtonShow: CardView
        private val pathButtonShowImage: ImageView
        private val pathButtonHideImage: ImageView
        private val pathButtonShowProgress: CircularProgressIndicator
        private val pathButtonDelete: CardView

        init {
            pathLength = view.findViewById(R.id.path_length)
            pathDate = view.findViewById(R.id.path_date)
            pathColor = view.findViewById(R.id.path_color)
            pathButtonShow = view.findViewById(R.id.path_button_show)
            pathButtonShowImage = view.findViewById(R.id.path_button_show_image)
            pathButtonHideImage = view.findViewById(R.id.show_all_paths_hide_image)
            pathButtonShowProgress = view.findViewById(R.id.path_button_show_progress)
            pathButtonDelete = view.findViewById(R.id.path_button_delete)
        }

        fun bind(
            path: PathInfoItemModel,
            itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
        ) {
            pathColor.setCardBackgroundColor(Color.parseColor(path.pathInfo.color))
            pathDate.text = formatTimestampDate(path.pathInfo.timestamp)
            pathLength.text = "13km" //TODO calculate path length
            pathButtonShow.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.SHOW)
            }
            pathButtonDelete.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.DELETE)
            }
            pathButtonShowImage.visibility = when (path.pathInfoItemShowButtonState) {
                PathInfoItemShowButtonState.DEFAULT -> View.VISIBLE
                else -> View.GONE
            }
            pathButtonHideImage.visibility = when (path.pathInfoItemShowButtonState) {
                PathInfoItemShowButtonState.HIDE -> View.VISIBLE
                else -> View.GONE
            }
            pathButtonShowProgress.visibility = when (path.pathInfoItemShowButtonState) {
                PathInfoItemShowButtonState.LOADING -> View.VISIBLE
                else -> View.GONE
            }
        }

        private fun formatTimestampDate(timestamp: Long): String {
            return dateFormat.format(Date(timestamp))
        }
    }

    enum class PathItemButtonType {
        SHOW, SHARE, DELETE
    }
}
