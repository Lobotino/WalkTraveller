package ru.lobotino.walktraveller.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.progressindicator.CircularProgressIndicator
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.ui.adapter.OuterPathsInfoAdapter
import ru.lobotino.walktraveller.ui.model.OuterPathsInfoListState
import ru.lobotino.walktraveller.ui.model.OuterPathsUiState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathsMenuButton
import ru.lobotino.walktraveller.ui.model.PathsToAction
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.DistanceInMetersToStringFormatter
import ru.lobotino.walktraveller.utils.Utils
import ru.lobotino.walktraveller.utils.ext.toColorInt

class OuterPathsMenuView : ConstraintLayout {

    private lateinit var titleButtonsHolder: ViewGroup

    private lateinit var showAllPathsButton: CardView
    private lateinit var showAllPathsProgress: CircularProgressIndicator
    private lateinit var showAllPathsDefaultImage: ImageView
    private lateinit var showAllPathsHideImage: ImageView

    private lateinit var deleteSelectedPathsButton: CardView

    private lateinit var pathsMenuBackButton: ImageView

    private lateinit var pathsInfoList: RecyclerView
    private lateinit var pathsEmptyListError: ViewGroup
    private lateinit var pathListProgress: CircularProgressIndicator

    private lateinit var confirmButton: Button

    private lateinit var onMenuTitleButtonClick: (PathsMenuButton) -> Unit
    private lateinit var itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
    private lateinit var itemShortTapListener: (Long) -> Unit
    private lateinit var itemLongTapListener: (Long) -> Unit

    private lateinit var pathsInfoListAdapter: OuterPathsInfoAdapter

    private var nowInSelectModeState = false

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
        val view = LayoutInflater.from(context).inflate(R.layout.outer_paths_menu, this)

        titleButtonsHolder = view.findViewById(R.id.title_buttons_holder)
        showAllPathsButton = view.findViewById(R.id.show_selected_paths_button)
        showAllPathsProgress = view.findViewById(R.id.show_all_paths_progress)
        showAllPathsDefaultImage = view.findViewById(R.id.show_all_paths_default_image)
        showAllPathsHideImage = view.findViewById(R.id.show_all_paths_hide_image)
        deleteSelectedPathsButton = view.findViewById(R.id.delete_selected_paths_button)
        pathsEmptyListError = view.findViewById(R.id.empty_paths_error)
        pathsMenuBackButton = view.findViewById(R.id.paths_menu_back_button)
        pathListProgress = view.findViewById(R.id.paths_list_progress)
        confirmButton = view.findViewById(R.id.confirm_button)

        pathsInfoList = view.findViewById<RecyclerView>(R.id.paths_list).apply {
            adapter = OuterPathsInfoAdapter(
                distanceFormatter = DistanceInMetersToStringFormatter(
                    context.getString(R.string.meters_short),
                    context.getString(R.string.kilometers_full),
                    context.getString(R.string.kilometers_short)
                ),
                mostCommonRatingColors = MostCommonRating.values().map { it.toColorInt(context) },
                itemButtonClickedListener = { pathId, itemButtonClickedType ->
                    itemButtonClickedListener(pathId, itemButtonClickedType)
                }, itemShortTapListener = { pathId ->
                    itemShortTapListener(pathId)
                }, itemLongTapListener = { pathId ->
                    itemLongTapListener(pathId)
                },
                context = getContext()
            ).also { pathsInfoListAdapter = it }

            if (itemAnimator is SimpleItemAnimator) {
                (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            }
        }
    }

    fun setupOnClickListeners(
        menuTitleButtonClickListener: (PathsMenuButton) -> Unit,
        confirmButtonClickListener: OnClickListener,
        itemButtonClickedListener: (Long, PathItemButtonType) -> Unit,
        itemShortTapListener: (Long) -> Unit,
        itemLongTapListener: (Long) -> Unit
    ) {
        confirmButton.setOnClickListener(confirmButtonClickListener)
        this.onMenuTitleButtonClick = menuTitleButtonClickListener
        this.itemButtonClickedListener = itemButtonClickedListener
        this.itemShortTapListener = itemShortTapListener
        this.itemLongTapListener = itemLongTapListener

        showAllPathsButton.setOnClickListener {
            onMenuTitleButtonClick(PathsMenuButton.ShowSelectedPaths)
        }
        deleteSelectedPathsButton.setOnClickListener {
            onMenuTitleButtonClick(PathsMenuButton.DeleteSelectedPaths)
        }
        pathsMenuBackButton.setOnClickListener {
            onMenuTitleButtonClick(PathsMenuButton.Back)
        }
    }

    fun syncState(outerPathsUiState: OuterPathsUiState) {
        showAllPathsButton.visibility = when (outerPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.GONE -> GONE
            else -> VISIBLE
        }
        showAllPathsDefaultImage.visibility = when (outerPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.DEFAULT -> VISIBLE
            else -> GONE
        }
        showAllPathsHideImage.visibility = when (outerPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.HIDE -> VISIBLE
            else -> GONE
        }
        showAllPathsProgress.visibility = when (outerPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> VISIBLE
            else -> GONE
        }

        when (outerPathsUiState.outerPathsInfoListState) {
            OuterPathsInfoListState.DEFAULT -> {
                pathsInfoList.visibility = VISIBLE
                pathListProgress.visibility = GONE
                pathsEmptyListError.visibility = GONE
                confirmButton.visibility = VISIBLE
            }

            OuterPathsInfoListState.LOADING -> {
                confirmButton.visibility = GONE
                pathsInfoList.visibility = GONE
                pathListProgress.visibility = VISIBLE
                pathsEmptyListError.visibility = GONE
            }

            OuterPathsInfoListState.EMPTY_LIST -> {
                confirmButton.visibility = GONE
                pathsInfoList.visibility = GONE
                pathListProgress.visibility = GONE
                pathsEmptyListError.visibility = VISIBLE
            }
        }

        syncSelectedMode(outerPathsUiState.inSelectMode)
    }

    private fun syncSelectedMode(inSelectMode: Boolean) {
        if (inSelectMode) {
            if (!nowInSelectModeState) {
                nowInSelectModeState = true
                switchSelectedPathsButtonsVisible(true)
            }
        } else {
            if (nowInSelectModeState) {
                nowInSelectModeState = false
                switchSelectedPathsButtonsVisible(false)
            }
        }
    }

    private fun switchSelectedPathsButtonsVisible(isVisible: Boolean) {
        val newMargin = if (isVisible) {
            0
        } else {
            Utils.convertDpToPixel(context, -44f).toInt()
        }
        val params = titleButtonsHolder.layoutParams as FrameLayout.LayoutParams
        val animator = ValueAnimator.ofInt(params.rightMargin, newMargin)
        animator.addUpdateListener { valueAnimator ->
            params.rightMargin = (valueAnimator.animatedValue as Int)
            titleButtonsHolder.requestLayout()
        }
        animator.interpolator = FastOutSlowInInterpolator()
        animator.setDuration(400)
        animator.start()
    }

    fun setPathsInfoItems(newPathsInfoList: List<MapPathInfo>) {
        pathsInfoListAdapter.setPathsInfoItems(newPathsInfoList)
    }

    fun deletePathInfoItem(pathsToDelete: PathsToAction) {
        pathsInfoListAdapter.deletePaths(pathsToDelete)
    }

    fun syncPathInfoItemState(pathInfoState: PathInfoItemState) {
        pathsInfoListAdapter.updatePaths(pathInfoState)
    }
}