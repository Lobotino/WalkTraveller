package ru.lobotino.walktraveller.ui.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.transition.Fade
import androidx.transition.TransitionManager
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.SegmentRating
import kotlin.properties.Delegates

class VolumeButtonsFeatureSuggestDialog(context: Context, onYesClicked: () -> Unit, onNoClicked: () -> Unit) : ConfirmYesNoDialog(
    context,
    context.getString(R.string.volume_buttons_feature_request_title),
    context.getString(R.string.volume_buttons_feature_request_desc),
    onYesClicked,
    onNoClicked
) {
    private var ratingWhiteColor by Delegates.notNull<Int>()
    private var ratingPerfectColor by Delegates.notNull<Int>()
    private var ratingGoodColor by Delegates.notNull<Int>()
    private var ratingNormalColor by Delegates.notNull<Int>()
    private var ratingBadlyColor by Delegates.notNull<Int>()
    private var ratingNoneColor by Delegates.notNull<Int>()

    private lateinit var parentViewGroup: ViewGroup
    private lateinit var ratingBadlyButton: CardView
    private lateinit var ratingNormalButton: CardView
    private lateinit var ratingGoodButton: CardView
    private lateinit var ratingPerfectButton: CardView
    private lateinit var ratingBadlyButtonStar: ImageView
    private lateinit var ratingNormalButtonStar: ImageView
    private lateinit var ratingGoodButtonStar: ImageView
    private lateinit var ratingPerfectButtonStar: ImageView
    private lateinit var howItWorksButtonImage: ImageView
    private lateinit var howItWorksLayout: ViewGroup

    private var tutorialOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setupWindowSettings()
        setContentView(R.layout.volume_buttons_suggest_confirm_dialog)
        initColors()
        initViews()
        setupTitle()
        setupDescription()
        setupCallbacks()
    }

    private fun initViews() {
        parentViewGroup = findViewById(R.id.parent_view_group)
        howItWorksButtonImage = findViewById(R.id.dropdown_list_icon)
        howItWorksLayout = findViewById(R.id.how_it_works_layout)
        ratingPerfectButton = findViewById(R.id.rating_perfect)
        ratingPerfectButtonStar = findViewById(R.id.rating_perfect_star)
        ratingGoodButton = findViewById(R.id.rating_good)
        ratingGoodButtonStar = findViewById(R.id.rating_good_star)
        ratingNormalButton = findViewById(R.id.rating_normal)
        ratingNormalButtonStar = findViewById(R.id.rating_normal_star)
        ratingBadlyButton = findViewById(R.id.rating_badly)
        ratingBadlyButtonStar = findViewById(R.id.rating_badly_star)
    }

    override fun setupCallbacks() {
        super.setupCallbacks()
        findViewById<ViewGroup>(R.id.how_it_works_button).apply {
            setOnClickListener {
                tutorialOpen = !tutorialOpen

                if (tutorialOpen) {
                    openTutorialView()
                } else {
                    hideTutorialView()
                }
            }
        }

        ratingPerfectButton.setOnClickListener { syncRatingButtons(SegmentRating.PERFECT) }
        ratingGoodButton.setOnClickListener { syncRatingButtons(SegmentRating.GOOD) }
        ratingNormalButton.setOnClickListener { syncRatingButtons(SegmentRating.NORMAL) }
        ratingBadlyButton.setOnClickListener { syncRatingButtons(SegmentRating.BADLY) }
    }

    private fun initColors() {
        context.let { context ->
            ratingWhiteColor = ContextCompat.getColor(context, R.color.white)
            ratingNoneColor = ContextCompat.getColor(context, R.color.rating_none_color)
            ratingPerfectColor = ContextCompat.getColor(context, R.color.rating_perfect_color)
            ratingGoodColor = ContextCompat.getColor(context, R.color.rating_good_color)
            ratingNormalColor = ContextCompat.getColor(context, R.color.rating_normal_color)
            ratingBadlyColor = ContextCompat.getColor(context, R.color.rating_badly_color)
        }
    }

    private fun openTutorialView() {
        TransitionManager.beginDelayedTransition(
            parentViewGroup,
            Fade().apply {
                addTarget(howItWorksLayout)
                duration = 200
            }
        )
        howItWorksLayout.isVisible = true

        howItWorksButtonImage.animate()
            .rotationBy(180f)
            .setDuration(200)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun hideTutorialView() {
        howItWorksLayout.isVisible = false

        howItWorksButtonImage.animate()
            .rotationBy(-180f)
            .setDuration(200)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun syncRatingButtons(currentRating: SegmentRating) {
        selectRatingButton(
            ratingPerfectButton,
            ratingPerfectButtonStar,
            ratingPerfectColor,
            ratingWhiteColor,
            currentRating == SegmentRating.PERFECT
        )
        selectRatingButton(
            ratingGoodButton,
            ratingGoodButtonStar,
            ratingGoodColor,
            ratingWhiteColor,
            currentRating == SegmentRating.GOOD
        )
        selectRatingButton(
            ratingNormalButton,
            ratingNormalButtonStar,
            ratingNormalColor,
            ratingWhiteColor,
            currentRating == SegmentRating.NORMAL
        )
        selectRatingButton(
            ratingBadlyButton,
            ratingBadlyButtonStar,
            ratingBadlyColor,
            ratingWhiteColor,
            currentRating == SegmentRating.BADLY
        )
    }

    private fun selectRatingButton(
        ratingButton: CardView,
        ratingButtonStar: ImageView,
        @ColorInt selectedColor: Int,
        @ColorInt unselectedColor: Int,
        selected: Boolean
    ) {
        ratingButton.setCardBackgroundColor(if (selected) selectedColor else unselectedColor)
        ImageViewCompat.setImageTintList(
            ratingButtonStar,
            ColorStateList.valueOf(if (selected) unselectedColor else selectedColor)
        )
    }
}
