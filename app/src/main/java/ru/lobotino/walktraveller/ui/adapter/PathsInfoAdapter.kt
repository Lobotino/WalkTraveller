package ru.lobotino.walktraveller.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.ui.model.PathInfoItemModel
import ru.lobotino.walktraveller.ui.model.PathInfoItemShareButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathsToAction
import ru.lobotino.walktraveller.usecases.interfaces.IDistanceToStringFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.properties.Delegates

open class PathsInfoAdapter(
    private val distanceFormatter: IDistanceToStringFormatter,
    private val mostCommonRatingColors: List<Int>,
    private val itemButtonClickedListener: (Long, PathItemButtonType) -> Unit,
    private val itemShortTapListener: (Long) -> Unit,
    private val itemLongTapListener: (Long) -> Unit,
    context: Context
) : RecyclerView.Adapter<PathsInfoAdapter.PathInfoItem>() {

    private var pathsItems: MutableList<PathInfoItemModel> = ArrayList<PathInfoItemModel>()

    protected var defaultItemBackgroundColor by Delegates.notNull<@ColorInt Int>()
    protected var selectedItemBackgroundColor by Delegates.notNull<@ColorInt Int>()

    init {
        defaultItemBackgroundColor = ContextCompat.getColor(context, R.color.white)
        selectedItemBackgroundColor = ContextCompat.getColor(context, R.color.primary_green_light)
    }

    fun getAllPathsItemsIds(): List<Long> {
        return pathsItems.map { it.pathInfo.pathId }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setPathsInfoItems(pathInfoItems: List<MapPathInfo>) {
        pathsItems = pathInfoItems.map { PathInfoItemModel(pathInfo = it) }.toMutableList()
        notifyDataSetChanged()
    }

    fun updatePaths(pathInfoItemState: PathInfoItemState) {
        when (val pathsToUpdate = pathInfoItemState.pathsToAction) {
            PathsToAction.All -> {
                updateAllItemsState(pathInfoItemState)
            }

            is PathsToAction.Single -> {
                updateItemState(pathsToUpdate.pathId, pathInfoItemState)
            }

            is PathsToAction.Multiple -> {
                updateItemsListState(pathsToUpdate.pathIds, pathInfoItemState)
            }
        }
    }

    private fun updateItemState(pathId: Long, pathInfoItemState: PathInfoItemState) {
        for (index in pathsItems.indices) {
            val item = pathsItems[index]
            if (item.pathInfo.pathId == pathId) {
                if (pathInfoItemState.showButtonState != null) {
                    item.showButtonState = pathInfoItemState.showButtonState
                }
                if (pathInfoItemState.shareButtonState != null) {
                    item.shareButtonState = pathInfoItemState.shareButtonState
                }
                if (pathInfoItemState.isSelected != null) {
                    item.isSelected = pathInfoItemState.isSelected
                }
                notifyItemChanged(index)
                return
            }
        }
    }

    private fun updateItemsListState(pathIds: List<Long>, pathInfoItemState: PathInfoItemState) {
        var updatedPathsCount = 0
        for (index in pathsItems.indices) {
            val item = pathsItems[index]
            if (pathIds.contains(item.pathInfo.pathId)) {
                if (pathInfoItemState.showButtonState != null) {
                    item.showButtonState = pathInfoItemState.showButtonState
                }
                if (pathInfoItemState.shareButtonState != null) {
                    item.shareButtonState = pathInfoItemState.shareButtonState
                }
                if (pathInfoItemState.isSelected != null) {
                    item.isSelected = pathInfoItemState.isSelected
                }
                notifyItemChanged(index)
                updatedPathsCount++

                if (updatedPathsCount >= pathIds.size) return
            }
        }
    }

    private fun updateAllItemsState(pathInfoItemState: PathInfoItemState) {
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
        if (pathInfoItemState.isSelected != null) {
            for (path in pathsItems) {
                path.isSelected = pathInfoItemState.isSelected
            }
        }
        notifyItemRangeChanged(0, pathsItems.size)
    }

    fun deletePaths(pathsToDelete: PathsToAction) {
        when (pathsToDelete) {
            PathsToAction.All -> {
                val pathsCount = pathsItems.size
                pathsItems.clear()
                notifyItemRangeRemoved(0, pathsCount)
            }

            is PathsToAction.Single -> {
                deletePathInfoItem(pathsToDelete.pathId)
            }

            is PathsToAction.Multiple -> {
                for (pathId in pathsToDelete.pathIds) {
                    deletePathInfoItem(pathId)
                }
            }
        }
    }

    private fun deletePathInfoItem(pathId: Long) {
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
                ),
            defaultItemBackgroundColor,
            selectedItemBackgroundColor
        )
    }

    override fun onBindViewHolder(holder: PathInfoItem, position: Int) {
        holder.bind(
            pathsItems[position],
            itemButtonClickedListener,
            itemShortTapListener,
            itemLongTapListener,
            distanceFormatter,
            mostCommonRatingColors
        )
    }

    override fun getItemCount(): Int {
        return pathsItems.size
    }

    open class PathInfoItem(
        view: View,
        @ColorInt private val defaultItemBackgroundColor: Int,
        @ColorInt private val selectedItemBackgroundColor: Int
    ) : RecyclerView.ViewHolder(view) {

        companion object {
            private const val DATE_FORMAT = "dd.MM.yyyy"
            private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH)
        }

        private lateinit var itemBackground: CardView
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

        protected var currentShowButtonState = PathInfoItemShowButtonState.DEFAULT

        init {
            prepareView(view)
        }

        protected open fun prepareView(view: View) {
            itemBackground = view.findViewById(R.id.item_background)
            pathLength = view.findViewById(R.id.path_length)
            pathDate = view.findViewById(R.id.path_date)
            pathMostCommonRatingColor = view.findViewById(R.id.path_most_common_rating_color)
            pathButtonShow = view.findViewById(R.id.path_button_show)
            pathButtonShowImage = view.findViewById(R.id.path_button_show_image)
            pathButtonHideImage = view.findViewById(R.id.show_selected_paths_hide_image)
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
            itemShortTapListener: (Long) -> Unit,
            itemLongTapListener: (Long) -> Unit,
            distanceFormatter: IDistanceToStringFormatter,
            mostCommonRatingColors: List<Int>
        ) {
            currentShowButtonState = path.showButtonState
            pathMostCommonRatingColor.setCardBackgroundColor(
                mostCommonRatingColors[path.pathInfo.mostCommonRating.ordinal]
            )
            pathDate.text = formatTimestampDate(path.pathInfo.timestamp)
            pathLength.text = distanceFormatter.formatDistance(path.pathInfo.length)
            pathButtonShow.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.Show(currentShowButtonState))
            }
            pathButtonDelete.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.Delete)
            }
            pathButtonShare.setOnClickListener {
                itemButtonClickedListener.invoke(path.pathInfo.pathId, PathItemButtonType.Share)
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
            itemBackground.setBackgroundColor(
                if (path.isSelected) {
                    selectedItemBackgroundColor
                } else {
                    defaultItemBackgroundColor
                }
            )
            itemBackground.setOnLongClickListener {
                itemLongTapListener(path.pathInfo.pathId)
                return@setOnLongClickListener true
            }
            itemBackground.setOnClickListener {
                itemShortTapListener(path.pathInfo.pathId)
            }
        }

        protected fun formatTimestampDate(timestamp: Long): String {
            return dateFormat.format(Date(timestamp))
        }
    }
}
