package ru.lobotino.walktraveller.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.cardview.widget.CardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.ui.model.PathInfoItemModel
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.usecases.interfaces.IDistanceToStringFormatter

class OuterPathsInfoAdapter(
    distanceFormatter: IDistanceToStringFormatter,
    mostCommonRatingColors: List<Int>,
    itemButtonClickedListener: (Long, PathItemButtonType) -> Unit,
    itemShortTapListener: (Long) -> Unit,
    itemLongTapListener: (Long) -> Unit,
    context: Context
) : PathsInfoAdapter(distanceFormatter, mostCommonRatingColors, itemButtonClickedListener, itemShortTapListener, itemLongTapListener, context) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathInfoItem {
        return OuterPathInfoItem(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.outer_path_info_item,
                    parent,
                    false
                ),
            defaultItemBackgroundColor,
            selectedItemBackgroundColor
        )
    }

    class OuterPathInfoItem(
        view: View,
        @ColorInt private val defaultItemBackgroundColor: Int,
        @ColorInt private val selectedItemBackgroundColor: Int
    ) : PathInfoItem(view, defaultItemBackgroundColor, selectedItemBackgroundColor) {

        private lateinit var itemBackground: CardView
        private lateinit var pathLength: TextView
        private lateinit var pathDate: TextView
        private lateinit var pathMostCommonRatingColor: CardView
        private lateinit var pathButtonShow: CardView
        private lateinit var pathButtonShowImage: ImageView
        private lateinit var pathButtonHideImage: ImageView
        private lateinit var pathButtonShowProgress: CircularProgressIndicator
        private lateinit var pathButtonDelete: CardView

        override fun prepareView(view: View) {
            itemBackground = view.findViewById(R.id.item_background)
            pathLength = view.findViewById(R.id.path_length)
            pathDate = view.findViewById(R.id.path_date)
            pathMostCommonRatingColor = view.findViewById(R.id.path_most_common_rating_color)
            pathButtonShow = view.findViewById(R.id.path_button_show)
            pathButtonShowImage = view.findViewById(R.id.path_button_show_image)
            pathButtonHideImage = view.findViewById(R.id.show_all_paths_hide_image)
            pathButtonShowProgress = view.findViewById(R.id.path_button_show_progress)
            pathButtonDelete = view.findViewById(R.id.path_button_delete)
        }

        override fun bind(
            path: PathInfoItemModel,
            itemButtonClickedListener: (Long, PathItemButtonType) -> Unit,
            itemShortTapListener: (Long) -> Unit,
            itemLongTapListener: (Long) -> Unit,
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
    }
}