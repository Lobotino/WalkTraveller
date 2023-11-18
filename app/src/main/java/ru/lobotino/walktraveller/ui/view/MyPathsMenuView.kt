package ru.lobotino.walktraveller.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.progressindicator.CircularProgressIndicator
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.ui.adapter.PathsInfoAdapter
import ru.lobotino.walktraveller.ui.model.MyPathsInfoListState
import ru.lobotino.walktraveller.ui.model.MyPathsUiState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.ui.model.ShowPathsFilterButtonState
import ru.lobotino.walktraveller.usecases.DistanceInMetersToStringFormatter
import ru.lobotino.walktraveller.utils.ext.toColorInt

class MyPathsMenuView : ConstraintLayout {

    private lateinit var showPathsFilterButton: CardView
    private lateinit var showPathsFilterRatedOnlyStateImage: View
    private lateinit var showPathsFilterAllInCommonStateImage: View

    private lateinit var showAllPathsButton: CardView
    private lateinit var showAllPathsProgress: CircularProgressIndicator
    private lateinit var showAllPathsDefaultImage: ImageView
    private lateinit var showAllPathsHideImage: ImageView

    private lateinit var pathsMenuBackButton: ImageView

    private lateinit var pathsInfoList: RecyclerView
    private lateinit var pathsEmptyListError: ViewGroup
    private lateinit var pathListProgress: CircularProgressIndicator

    private lateinit var itemButtonClickedListener: (Long, PathItemButtonType) -> Unit

    private lateinit var pathsInfoListAdapter: PathsInfoAdapter

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initView(context)
    }

    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    ) {
        initView(context)
    }

    private fun initView(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.my_paths_menu, this)

        showAllPathsButton = view.findViewById(R.id.show_all_paths_button)
        showAllPathsProgress = view.findViewById(R.id.show_all_paths_progress)
        showAllPathsDefaultImage = view.findViewById(R.id.show_all_paths_default_image)
        showAllPathsHideImage = view.findViewById(R.id.show_all_paths_hide_image)

        showPathsFilterButton =
            view.findViewById(R.id.show_paths_filter_button)
        showPathsFilterAllInCommonStateImage =
            view.findViewById(R.id.show_paths_filter_button_all_in_common_state)
        showPathsFilterRatedOnlyStateImage =
            view.findViewById(R.id.show_paths_filter_button_rated_only_state)

        pathsEmptyListError = view.findViewById(R.id.empty_paths_error)
        pathsMenuBackButton = view.findViewById(R.id.paths_menu_back_button)
        pathListProgress = view.findViewById(R.id.paths_list_progress)

        pathsInfoList = view.findViewById<RecyclerView>(R.id.paths_list).apply {
            adapter = PathsInfoAdapter(
                DistanceInMetersToStringFormatter(
                    context.getString(R.string.meters_short),
                    context.getString(R.string.kilometers_full),
                    context.getString(R.string.kilometers_short)
                ),
                MostCommonRating.values().map { it.toColorInt(context) }
            ) { pathId, itemButtonClickedType ->
                itemButtonClickedListener(pathId, itemButtonClickedType)
            }.also { pathsInfoListAdapter = it }

            if (itemAnimator is SimpleItemAnimator) {
                (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            }
        }
    }

    fun setupOnClickListeners(
        showAllPathsButtonClickListener: OnClickListener,
        showPathsFilterButtonClickListener: OnClickListener,
        pathsMenuBackButtonClickListener: OnClickListener,
        itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
    ) {
        showAllPathsButton.setOnClickListener(showAllPathsButtonClickListener)
        showPathsFilterButton.setOnClickListener(showPathsFilterButtonClickListener)
        pathsMenuBackButton.setOnClickListener(pathsMenuBackButtonClickListener)
        this.itemButtonClickedListener = itemButtonClickedListener
    }

    fun syncState(myPathsUiState: MyPathsUiState) {
        showAllPathsButton.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.GONE -> GONE
            else -> VISIBLE
        }
        showAllPathsDefaultImage.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.DEFAULT -> VISIBLE
            else -> GONE
        }
        showAllPathsHideImage.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.HIDE -> VISIBLE
            else -> GONE
        }
        showAllPathsProgress.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> VISIBLE
            else -> GONE
        }
        showPathsFilterButton.visibility = when (myPathsUiState.showPathsFilterButtonState) {
            ShowPathsFilterButtonState.GONE -> GONE
            else -> VISIBLE
        }
        showPathsFilterAllInCommonStateImage.visibility =
            when (myPathsUiState.showPathsFilterButtonState) {
                ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> VISIBLE
                else -> GONE
            }
        showPathsFilterRatedOnlyStateImage.visibility =
            when (myPathsUiState.showPathsFilterButtonState) {
                ShowPathsFilterButtonState.RATED_ONLY -> VISIBLE
                else -> GONE
            }

        when (myPathsUiState.pathsInfoListState) {
            MyPathsInfoListState.DEFAULT -> {
                pathsInfoList.visibility = VISIBLE
                pathListProgress.visibility = GONE
                pathsEmptyListError.visibility = GONE
            }

            MyPathsInfoListState.LOADING -> {
                pathsInfoList.visibility = GONE
                pathListProgress.visibility = VISIBLE
                pathsEmptyListError.visibility = GONE
            }

            MyPathsInfoListState.EMPTY_LIST -> {
                pathsInfoList.visibility = GONE
                pathListProgress.visibility = GONE
                pathsEmptyListError.visibility = VISIBLE
            }
        }
    }

    fun setPathsInfoItems(newPathsInfoList: List<MapPathInfo>) {
        pathsInfoListAdapter.setPathsInfoItems(newPathsInfoList)
    }

    fun deletePathInfoItem(pathId: Long) {
        pathsInfoListAdapter.deletePathInfoItem(pathId)
    }

    fun syncPathInfoItemState(pathInfoState: PathInfoItemState) {
        if (pathInfoState.pathId == -1L) {
            pathsInfoListAdapter.updateAllItemsState(pathInfoState)
        } else {
            pathsInfoListAdapter.updateItemState(pathInfoState)
        }
    }
}