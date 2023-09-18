package ru.lobotino.walktraveller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.OptimizePathsSettingsRepository
import ru.lobotino.walktraveller.utils.ext.openNavigationMenu

class SettingsFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var optimizePathsSlider: Slider

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
            initViewModel()
        }
    }

    private fun initViewModel() {
        //TODO
    }

    private fun initViews(view: View) {
        toolbar = view.findViewById<Toolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener {
                openNavigationMenu()
            }
        }

        optimizePathsSlider = view.findViewById<Slider>(R.id.optimizing_paths_slider).apply {
            value = OptimizePathsSettingsRepository(
                sharedPreferences = requireContext().getSharedPreferences(
                    App.SHARED_PREFS_TAG,
                    AppCompatActivity.MODE_PRIVATE
                )
            ).getOptimizePathsApproximationDistance() ?: 0f //fixme move to viewmodel

            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    //do nothing
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    OptimizePathsSettingsRepository(
                        sharedPreferences = requireContext().getSharedPreferences(
                            App.SHARED_PREFS_TAG,
                            AppCompatActivity.MODE_PRIVATE
                        )
                    ).setOptimizePathsApproximationDistance(slider.value) //fixme move to viewmodel
                }
            })
        }
    }
}