package ru.lobotino.walktraveller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.di.SettingsViewModelFactory
import ru.lobotino.walktraveller.repositories.OptimizePathsSettingsRepository
import ru.lobotino.walktraveller.utils.ext.openNavigationMenu
import ru.lobotino.walktraveller.viewmodels.SettingsViewModel

class SettingsFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var optimizePathsSlider: Slider

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_settings,
            container,
            false
        ).also { view ->
            initViews(view)
            initViewModel(savedInstanceState)
        }
    }

    private fun initViewModel(bundle: Bundle?) {
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(
                optimizePathsSettingsRepository = OptimizePathsSettingsRepository(
                    sharedPreferences = requireContext().getSharedPreferences(
                        App.SHARED_PREFS_TAG,
                        AppCompatActivity.MODE_PRIVATE
                    )
                ),
                owner = this,
                bundle = bundle
            )
        )[SettingsViewModel::class.java].apply {
            observeSettingsUiState
                .onEach { uiState ->
                    optimizePathsSlider.value = uiState.optimizePathsValue
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)
        }
    }

    private fun initViews(view: View) {
        toolbar = view.findViewById<Toolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener {
                openNavigationMenu()
            }
        }

        optimizePathsSlider = view.findViewById<Slider>(R.id.optimizing_paths_slider).apply {
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    // do nothing
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    viewModel.onOptimizePathsSettingsChange(slider.value)
                }
            })
        }
    }
}
