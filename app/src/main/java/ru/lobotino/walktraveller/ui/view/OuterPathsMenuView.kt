package ru.lobotino.walktraveller.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
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
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.DistanceInMetersToStringFormatter
import ru.lobotino.walktraveller.utils.ext.toColorInt

class OuterPathsMenuView : ConstraintLayout {

    private lateinit var showAllPathsButton: CardView
    private lateinit var showAllPathsProgress: CircularProgressIndicator
    private lateinit var showAllPathsDefaultImage: ImageView
    private lateinit var showAllPathsHideImage: ImageView

    private lateinit var pathsMenuBackButton: ImageView

    private lateinit var pathsInfoList: RecyclerView
    private lateinit var pathsEmptyListError: ViewGroup
    private lateinit var pathListProgress: CircularProgressIndicator

    private lateinit var confirmButton: Button

    private lateinit var itemButtonClickedListener: (Long, PathItemButtonType) -> Unit

    private lateinit var pathsInfoListAdapter: OuterPathsInfoAdapter

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

        showAllPathsButton = view.findViewById(R.id.show_all_paths_button)
        showAllPathsProgress = view.findViewById(R.id.show_all_paths_progress)
        showAllPathsDefaultImage = view.findViewById(R.id.show_all_paths_default_image)
        showAllPathsHideImage = view.findViewById(R.id.show_all_paths_hide_image)

        pathsEmptyListError = view.findViewById(R.id.empty_paths_error)
        pathsMenuBackButton = view.findViewById(R.id.paths_menu_back_button)
        pathListProgress = view.findViewById(R.id.paths_list_progress)
        confirmButton = view.findViewById(R.id.confirm_button)

        pathsInfoList = view.findViewById<RecyclerView>(R.id.paths_list).apply {
            adapter = OuterPathsInfoAdapter(
                DistanceInMetersToStringFormatter(
                    context.getString(R.string.meters_full),
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
        pathsMenuBackButtonClickListener: OnClickListener,
        confirmButtonClickListener: OnClickListener,
        itemButtonClickedListener: (Long, PathItemButtonType) -> Unit
    ) {
        showAllPathsButton.setOnClickListener(showAllPathsButtonClickListener)
        pathsMenuBackButton.setOnClickListener(pathsMenuBackButtonClickListener)
        confirmButton.setOnClickListener(confirmButtonClickListener)
        this.itemButtonClickedListener = itemButtonClickedListener
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