package ru.lobotino.walktraveller.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.di.SettingsViewModelFactory
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.OptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.TileSourceRepository
import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository
import ru.lobotino.walktraveller.repositories.permissions.NotificationsPermissionsRepository
import ru.lobotino.walktraveller.ui.model.SettingsUiState
import ru.lobotino.walktraveller.usecases.TileSourceInteractor
import ru.lobotino.walktraveller.utils.ResourceManager
import ru.lobotino.walktraveller.utils.ext.openNavigationMenu
import ru.lobotino.walktraveller.viewmodels.SettingsViewModel


class SettingsFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var optimizePathsSlider: Slider
    private lateinit var mapStyleSpinner: Spinner
    private lateinit var buttonDisableBatteryOptimization: Button
    private lateinit var buttonCheckNotificationSettings: Button
    private lateinit var buttonCheckGeolocationSettings: Button
    private lateinit var mainView: View

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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
        val sharedPreferences = requireContext().getSharedPreferences(
            App.SHARED_PREFS_TAG,
            AppCompatActivity.MODE_PRIVATE
        )
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(
                optimizePathsSettingsRepository = OptimizePathsSettingsRepository(
                    sharedPreferences
                ),
                tileSourceInteractor = TileSourceInteractor(TileSourceRepository(sharedPreferences = sharedPreferences)),
                notificationPermissionsRepository = NotificationsPermissionsRepository(
                    this,
                    requireContext().applicationContext
                ),
                geoPermissionsRepository = GeoPermissionsRepository(
                    this,
                    requireContext().applicationContext
                ),
                resourceManager = ResourceManager(requireContext().applicationContext),
                owner = this,
                bundle = bundle
            )
        )[SettingsViewModel::class.java].apply {
            observeSettingsUiState
                .onEach { uiState ->
                    syncUiState(uiState)
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)

            observeNotifications.onEach { notification ->
                showSnackbar(notification)
            }.launchIn(viewLifecycleOwner.lifecycleScope)
        }
    }

    private fun syncUiState(uiState: SettingsUiState) {
        optimizePathsSlider.value =
            if (uiState.optimizePathsValue in optimizePathsSlider.valueFrom..optimizePathsSlider.valueTo) {
                uiState.optimizePathsValue
            } else {
                optimizePathsSlider.valueFrom
            }

        mapStyleSpinner.setSelection(
            TileSourceType.values().indexOf(uiState.mapStyleValue)
        )
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

        mapStyleSpinner = view.findViewById<Spinner?>(R.id.map_tiles_sources_spinner).apply {
            adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_map_tile_source,
                TileSourceType.values().map { it.simpleName }
            )
            onItemSelectedListener = object : OnItemSelectedListener,
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    if (position <= TileSourceType.values().size) {
                        viewModel.onMapStyleChosen(TileSourceType.values()[position])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onNavigationItemSelected(item: MenuItem): Boolean {
                    return true
                }
            }
        }

        buttonDisableBatteryOptimization =
            view.findViewById<Button>(R.id.faq_disable_battery_safe_button).apply {
                setOnClickListener {
                    sendDisableBatterySafeIntent()
                }
            }

        buttonCheckNotificationSettings =
            view.findViewById<Button?>(R.id.faq_check_notification_button).apply {
                setOnClickListener {
                    viewModel.onCheckNotificationSettingsClick()
                }
            }

        buttonCheckGeolocationSettings =
            view.findViewById<Button?>(R.id.faq_check_geolocation_button).apply {
                setOnClickListener {
                    viewModel.onCheckGeolocationSettingsClick()
                }
            }

        mainView = view.findViewById(R.id.main_view)
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(mainView, message, duration).show()
    }

    @SuppressLint("BatteryLife")
    private fun sendDisableBatterySafeIntent() {
        val context = context ?: return
        val powerManager = getSystemService(context, PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showSnackbar(getString(R.string.permission_granted))
        } else {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                val uri = Uri.parse("package:" + context.packageName)
                intent.setData(uri)
                context.startActivity(intent)
            } catch (exception: ActivityNotFoundException) {
                showSnackbar(getString(R.string.error_permissions_denied))
            }
        }
    }
}
