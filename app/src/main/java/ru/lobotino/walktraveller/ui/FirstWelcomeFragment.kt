package ru.lobotino.walktraveller.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.AppScreen
import ru.lobotino.walktraveller.ui.adapter.WelcomeSlidesAdapter
import ru.lobotino.walktraveller.ui.model.TrackAnimationMode
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState
import ru.lobotino.walktraveller.ui.model.WelcomePage
import ru.lobotino.walktraveller.utils.ext.navigateTo
import ru.lobotino.walktraveller.viewmodels.FirstWelcomeViewModel

class FirstWelcomeFragment : Fragment() {

    private lateinit var pager: ViewPager2
    private lateinit var skipButton: Button
    private lateinit var primaryButton: Button
    private lateinit var dotsContainer: LinearLayout
    private val dots = mutableListOf<ImageView>()

    private var viewModel: FirstWelcomeViewModel? = null
    private var lastButtonState: WelcomeContinueButtonState = WelcomeContinueButtonState.DEFAULT

    private val pages: List<WelcomePage> = listOf(
        WelcomePage.Tutorial(
            TrackAnimationMode.RECORD,
            R.string.welcome_slide_record_title,
            R.string.welcome_slide_record_subtitle,
        ),
        WelcomePage.Tutorial(
            TrackAnimationMode.RATE,
            R.string.welcome_slide_rate_title,
            R.string.welcome_slide_rate_subtitle,
        ),
        WelcomePage.Tutorial(
            TrackAnimationMode.VOLUME,
            R.string.welcome_slide_volume_title,
            R.string.welcome_slide_volume_subtitle,
        ),
        WelcomePage.Consent,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.first_welcome_fragment, container, false).also { view ->
            initViews(view)
            initViewModel()
            updateForPage(pager.currentItem)
        }
    }

    override fun onResume() {
        super.onResume()
        (requireContext().applicationContext as App).analyticsTracker
            .track(AnalyticsEvent.ScreenView("welcome"))
    }

    private fun initViews(view: View) {
        pager = view.findViewById(R.id.welcome_pager)
        skipButton = view.findViewById(R.id.welcome_skip_button)
        primaryButton = view.findViewById(R.id.welcome_primary_button)
        dotsContainer = view.findViewById(R.id.welcome_dots_container)

        pager.adapter = WelcomeSlidesAdapter(pages) { isChecked ->
            viewModel?.onPrivacyPolicyCheckedChanged(isChecked)
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateForPage(position)
            }
        })

        buildDots()

        skipButton.setOnClickListener {
            pager.setCurrentItem(pages.lastIndex, true)
        }
        primaryButton.setOnClickListener {
            if (pager.currentItem < pages.lastIndex) {
                pager.currentItem = pager.currentItem + 1
            } else {
                viewModel?.onContinueButtonClick()
            }
        }
    }

    private fun buildDots() {
        dots.clear()
        dotsContainer.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        for (i in pages.indices) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                setImageResource(R.drawable.bg_welcome_dot)
            }
            dots.add(dot)
            dotsContainer.addView(dot)
        }
    }

    private fun initViewModel() {
        viewModel =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                .create(FirstWelcomeViewModel::class.java).apply {
                    setUserInfoRepository(
                        UserInfoRepository(
                            requireContext().getSharedPreferences(
                                App.SHARED_PREFS_TAG,
                                AppCompatActivity.MODE_PRIVATE,
                            ),
                        ),
                    )

                    observeContinueButtonStateChanges.onEach { state ->
                        lastButtonState = state
                        if (pager.currentItem == pages.lastIndex) {
                            applyConsentButtonState(state)
                        }
                    }.launchIn(viewLifecycleOwner.lifecycleScope)

                    onContinueListener = {
                        navigateTo(AppScreen.MAP_SCREEN, arguments?.getParcelable(EXTRA_DATA_URI))
                    }
                }
    }

    private fun updateForPage(position: Int) {
        dots.forEachIndexed { index, dot ->
            dot.alpha = if (index == position) 1f else 0.3f
        }
        val isLast = position == pages.lastIndex
        skipButton.visibility = if (isLast) View.GONE else View.VISIBLE
        if (isLast) {
            applyConsentButtonState(lastButtonState)
        } else {
            primaryButton.isEnabled = true
            primaryButton.text = getString(R.string.welcome_next_button)
        }
    }

    private fun applyConsentButtonState(state: WelcomeContinueButtonState) {
        when (state) {
            WelcomeContinueButtonState.DEFAULT -> {
                primaryButton.isEnabled = true
                primaryButton.text = getString(R.string.welcome_start_button)
            }

            WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST -> {
                primaryButton.isEnabled = false
                primaryButton.text =
                    getString(R.string.welcome_continue_button_need_to_agreement_first)
            }
        }
    }

    companion object {
        private const val EXTRA_DATA_URI = "EXTRA_DATA_URI"

        fun newInstance(extraData: Uri?): FirstWelcomeFragment {
            return FirstWelcomeFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA_URI, extraData)
                }
            }
        }
    }
}
