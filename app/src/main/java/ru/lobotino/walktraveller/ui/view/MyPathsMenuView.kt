package ru.lobotino.walktraveller.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
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
import ru.lobotino.walktraveller.ui.model.PathsMenuButton
import ru.lobotino.walktraveller.ui.model.PathsToAction
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.ui.model.ShowPathsFilterButtonState
import ru.lobotino.walktraveller.usecases.DistanceInMetersToStringFormatter
import ru.lobotino.walktraveller.utils.Utils
import ru.lobotino.walktraveller.utils.ext.toColorInt

class MyPathsMenuView : ConstraintLayout {

    private lateinit var showPathsFilterButton: CardView
    private lateinit var showPathsFilterRatedOnlyStateImage: View
    private lateinit var showPathsFilterAllInCommonStateImage: View

    private lateinit var showSelectedPathsButton: CardView
    private lateinit var showSelectedPathsProgress: CircularProgressIndicator
    private lateinit var showSelectedPathsDefaultImage: ImageView
    private lateinit var showSelectedPathsHideImage: ImageView

    private lateinit var pathsMenuBackButton: ImageView

    private lateinit var pathsInfoList: RecyclerView
    private lateinit var pathsEmptyListError: ViewGroup
    private lateinit var pathListProgress: CircularProgressIndicator

    private lateinit var menuTitle: TextView
    private lateinit var titleButtonsHolder: ViewGroup
    private lateinit var selectAllPathsButton: CardView
    private lateinit var shareSelectedPathsButton: CardView
    private lateinit var deleteSelectedPathsButton: CardView

    private lateinit var onMenuTitleButtonClick: (PathsMenuButton) -> Unit
    private lateinit var itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
    private lateinit var itemShortTapListener: (Long) -> Unit
    private lateinit var itemLongTapListener: (Long) -> Unit

    private lateinit var pathsInfoListAdapter: PathsInfoAdapter

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
        val view = LayoutInflater.from(context).inflate(R.layout.my_paths_menu, this)

        showSelectedPathsProgress = view.findViewById(R.id.show_selected_paths_progress)
        showSelectedPathsDefaultImage = view.findViewById(R.id.show_selected_paths_default_image)
        showSelectedPathsHideImage = view.findViewById(R.id.show_selected_paths_hide_image)

        showPathsFilterAllInCommonStateImage =
            view.findViewById(R.id.show_paths_filter_button_all_in_common_state)
        showPathsFilterRatedOnlyStateImage =
            view.findViewById(R.id.show_paths_filter_button_rated_only_state)

        pathsEmptyListError = view.findViewById(R.id.empty_paths_error)
        pathListProgress = view.findViewById(R.id.paths_list_progress)

        menuTitle = view.findViewById(R.id.paths_list_title)
        titleButtonsHolder = view.findViewById(R.id.title_buttons_holder)

        showSelectedPathsButton = view.findViewById<CardView>(R.id.show_selected_paths_button).apply {
            setOnClickListener {
                onMenuTitleButtonClick(PathsMenuButton.ShowSelectedPaths)
            }
        }
        showPathsFilterButton = view.findViewById<CardView>(R.id.show_paths_filter_button).apply {
            setOnClickListener {
                onMenuTitleButtonClick(PathsMenuButton.FilterPathsColor)
            }
        }
        pathsMenuBackButton = view.findViewById<ImageView>(R.id.paths_menu_back_button).apply {
            setOnClickListener {
                onMenuTitleButtonClick(PathsMenuButton.Back)
            }
        }
        selectAllPathsButton = view.findViewById<CardView>(R.id.select_all_paths_button).apply {
            setOnClickListener {
                onMenuTitleButtonClick(PathsMenuButton.SelectAll(pathsInfoListAdapter.getAllPathsItemsIds()))
            }
        }
        shareSelectedPathsButton = view.findViewById<CardView>(R.id.share_selected_paths_button).apply {
            setOnClickListener {
                onMenuTitleButtonClick(PathsMenuButton.ShareSelectedPaths)
            }
        }
        deleteSelectedPathsButton = view.findViewById<CardView>(R.id.delete_selected_paths_button).apply {
            setOnClickListener {
                onMenuTitleButtonClick(PathsMenuButton.DeleteSelectedPaths)
            }
        }

        pathsInfoList = view.findViewById<RecyclerView>(R.id.paths_list).apply {
            adapter = PathsInfoAdapter(
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
                }, context = getContext()
            ).also { pathsInfoListAdapter = it }

            if (itemAnimator is SimpleItemAnimator) {
                (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            }
        }
    }

    fun setupOnClickListeners(
        menuTitleButtonClickListener: (PathsMenuButton) -> Unit,
        itemButtonClickedListener: (Long, PathItemButtonType) -> Unit,
        itemShortTapListener: (Long) -> Unit,
        itemLongTapListener: (Long) -> Unit,
    ) {
        this.onMenuTitleButtonClick = menuTitleButtonClickListener
        this.itemButtonClickedListener = itemButtonClickedListener
        this.itemShortTapListener = itemShortTapListener
        this.itemLongTapListener = itemLongTapListener
    }

    fun syncState(myPathsUiState: MyPathsUiState) {
        showSelectedPathsButton.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.GONE -> GONE
            else -> VISIBLE
        }
        showSelectedPathsDefaultImage.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.DEFAULT -> VISIBLE
            else -> GONE
        }
        showSelectedPathsHideImage.visibility = when (myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.HIDE -> VISIBLE
            else -> GONE
        }
        showSelectedPathsProgress.visibility = when (myPathsUiState.showPathsButtonState) {
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
                titleButtonsHolder.visibility = VISIBLE
                pathsInfoList.visibility = VISIBLE
                pathListProgress.visibility = GONE
                pathsEmptyListError.visibility = GONE
            }

            MyPathsInfoListState.LOADING -> {
                titleButtonsHolder.visibility = GONE
                pathsInfoList.visibility = GONE
                pathListProgress.visibility = VISIBLE
                pathsEmptyListError.visibility = GONE
            }

            MyPathsInfoListState.EMPTY_LIST -> {
                titleButtonsHolder.visibility = GONE
                pathsInfoList.visibility = GONE
                pathListProgress.visibility = GONE
                pathsEmptyListError.visibility = VISIBLE
            }
        }

        syncSelectMode(myPathsUiState.inSelectMode)
    }

    private fun syncSelectMode(inSelectMode: Boolean) {
        if (inSelectMode) {
            if (!nowInSelectModeState) {
                nowInSelectModeState = true
                switchViewVisibilityWithAnimation(menuTitle, false)
                switchViewVisibilityWithAnimation(selectAllPathsButton, true)
                switchSelectedPathsButtonsVisible(true)
            }
        } else {
            if (nowInSelectModeState) {
                nowInSelectModeState = false
                switchViewVisibilityWithAnimation(menuTitle, true)
                switchViewVisibilityWithAnimation(selectAllPathsButton, false)
                switchSelectedPathsButtonsVisible(false)
            }
        }
    }

    private fun switchViewVisibilityWithAnimation(view: View, isVisible: Boolean) {
        view.animate()
            .alpha(if (isVisible) 1f else 0f)
            .setDuration(if (isVisible) 250 else 150).apply {
                setListener(
                    if (isVisible) {
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                view.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                view.visibility = View.GONE
                            }
                        }
                    }
                )
            }
    }

    private fun switchSelectedPathsButtonsVisible(isVisible: Boolean) {
        val newMargin = if (isVisible) {
            0
        } else {
            Utils.convertDpToPixel(context, -89f).toInt()
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
