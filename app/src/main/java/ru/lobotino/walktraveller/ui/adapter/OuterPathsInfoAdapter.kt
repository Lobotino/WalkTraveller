package ru.lobotino.walktraveller.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
    itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
) : PathsInfoAdapter(distanceFormatter, mostCommonRatingColors, itemButtonClickedListener) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathInfoItem {
        return OuterPathInfoItem(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.outer_path_info_item,
                    parent,
                    false
                )
        )
    }

    class OuterPathInfoItem(view: View) : PathInfoItem(view) {
        private lateinit var pathLength: TextView
        private lateinit var pathDate: TextView
        private lateinit var pathMostCommonRatingColor: CardView
        private lateinit var pathButtonShow: CardView
        private lateinit var pathButtonShowImage: ImageView
        private lateinit var pathButtonHideImage: ImageView
        private lateinit var pathButtonShowProgress: CircularProgressIndicator
        private lateinit var pathButtonDelete: CardView

        override fun prepareView(view: View) {
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
        }
    }
}