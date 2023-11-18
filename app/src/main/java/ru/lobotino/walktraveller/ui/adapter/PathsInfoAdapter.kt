package ru.lobotino.walktraveller.ui.adapter

import android.annotation.SuppressLint
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
import ru.lobotino.walktraveller.usecases.interfaces.IDistanceToStringFormatter
import java.text.SimpleDateFormat
import java.util.*
import ru.lobotino.walktraveller.ui.model.PathInfoItemShareButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType


open class PathsInfoAdapter(
    private val distanceFormatter: IDistanceToStringFormatter,
    private val mostCommonRatingColors: List<Int>,
    private val itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
) : RecyclerView.Adapter<PathsInfoAdapter.PathInfoItem>() {

    private var pathsItems: MutableList<PathInfoItemModel> = ArrayList<PathInfoItemModel>()

    @SuppressLint("NotifyDataSetChanged")
    fun setPathsInfoItems(pathInfoItems: List<MapPathInfo>) {
        pathsItems = pathInfoItems.map { PathInfoItemModel(pathInfo = it) }.toMutableList()
        notifyDataSetChanged()
    }

    fun updateItemState(pathInfoItemState: PathInfoItemState) {
        for (index in pathsItems.indices) {
            val item = pathsItems[index]
            if (item.pathInfo.pathId == pathInfoItemState.pathId) {
                if (pathInfoItemState.showButtonState != null) {
                    item.showButtonState = pathInfoItemState.showButtonState
                }
                if (pathInfoItemState.shareButtonState != null) {
                    item.shareButtonState = pathInfoItemState.shareButtonState
                }
                notifyItemChanged(index)
                return
            }
        }
    }

    fun updateAllItemsState(pathInfoItemState: PathInfoItemState) {
        if (pathInfoItemState.showButtonState != null) {
            for (path in pathsItems) {
                path.showButtonState = pathInfoItemState.showButtonState
            }
        }
        if (pathInfoItemState.shareButtonState != null) {
            for (path in pathsItems) {
                path.shareButtonState = pathInfoItemState.shareButtonState
            }
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
                    R.layout.my_path_info_item,
                    parent,
                    false
                )
        )
    }

    override fun onBindViewHolder(holder: PathInfoItem, position: Int) {
        holder.bind(
            pathsItems[position],
            itemButtonClickedListener,
            distanceFormatter,
            mostCommonRatingColors
        )
    }

    override fun getItemCount(): Int {
        return pathsItems.size
    }

    open class PathInfoItem(view: View) : RecyclerView.ViewHolder(view) {

        companion object {
            private const val DATE_FORMAT = "dd.MM.yyyy"
            private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH)
        }

        private lateinit var pathLength: TextView
        private lateinit var pathDate: TextView
        private lateinit var pathMostCommonRatingColor: CardView
        private lateinit var pathButtonShow: CardView
        private lateinit var pathButtonShowImage: ImageView
        private lateinit var pathButtonHideImage: ImageView
        private lateinit var pathButtonShareImage: ImageView
        private lateinit var pathButtonShowProgress: CircularProgressIndicator
        private lateinit var pathButtonShareProgress: CircularProgressIndicator
        private lateinit var pathButtonDelete: CardView
        private lateinit var pathButtonShare: CardView
        private lateinit var outerPathImage: ImageView

        init {
            prepareView(view)
        }

        protected open fun prepareView(view: View) {
            pathLength = view.findViewById(R.id.path_length)
            pathDate = view.findViewById(R.id.path_date)
            pathMostCommonRatingColor = view.findViewById(R.id.path_most_common_rating_color)
            pathButtonShow = view.findViewById(R.id.path_button_show)
            pathButtonShowImage = view.findViewById(R.id.path_button_show_image)
            pathButtonHideImage = view.findViewById(R.id.show_all_paths_hide_image)
            pathButtonShowProgress = view.findViewById(R.id.path_button_show_progress)
            pathButtonDelete = view.findViewById(R.id.path_button_delete)
            pathButtonShare = view.findViewById(R.id.path_button_share)
            pathButtonShareImage = view.findViewById(R.id.path_button_share_image)
            pathButtonShareProgress = view.findViewById(R.id.path_button_share_progress)
            outerPathImage = view.findViewById(R.id.outer_paths_image)
        }

        open fun bind(
            path: PathInfoItemModel,
            itemButtonClickedListener: (Long, PathItemButtonType) -> Unit,
            distanceFormatter: IDistanceToStringFormatter,
            mostCommonRatingColors: List<Int>
        ) {
            pathMostCommonRatingColor.setCardBackgroundColor(mostCommonRatingColors[path.pathInfo.mostCommonRating.ordinal])
            pathDate.text = formatTimestampDate(path.pathInfo.timestamp)
            pathLength.text = distanceFormatter.formatDistance(path.pathInfo.length)
            pathButtonShow.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.SHOW)
            }
            pathButtonDelete.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.DELETE)
            }
            pathButtonShare.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.SHARE)
            }
            pathButtonShowImage.visibility = when (path.showButtonState) {
                PathInfoItemShowButtonState.DEFAULT -> View.VISIBLE
                else -> View.GONE
            }
            pathButtonHideImage.visibility = when (path.showButtonState) {
                PathInfoItemShowButtonState.HIDE -> View.VISIBLE
                else -> View.GONE
            }
            pathButtonShowProgress.visibility = when (path.showButtonState) {
                PathInfoItemShowButtonState.LOADING -> View.VISIBLE
                else -> View.GONE
            }
            pathButtonShareImage.visibility = when (path.shareButtonState) {
                PathInfoItemShareButtonState.DEFAULT -> View.VISIBLE
                else -> View.GONE
            }
            pathButtonShareProgress.visibility = when (path.shareButtonState) {
                PathInfoItemShareButtonState.LOADING -> View.VISIBLE
                else -> View.GONE
            }
            outerPathImage.visibility = if (path.pathInfo.isOuterPath) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        protected fun formatTimestampDate(timestamp: Long): String {
            return dateFormat.format(Date(timestamp))
        }
    }
}
