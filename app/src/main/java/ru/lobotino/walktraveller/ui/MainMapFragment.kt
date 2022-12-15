package ru.lobotino.walktraveller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.GeoPermissionsRepository
import ru.lobotino.walktraveller.usecases.PermissionsInteractor
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MainMapFragment : Fragment() {

    private lateinit var mapViewModel: MapViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initViewModel()
    }

    private fun initViewModel() {
        if (activity != null && context != null) {
            mapViewModel =
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                    .create(MapViewModel::class.java).apply {
                        setPermissionsInteractor(
                            PermissionsInteractor(
                                GeoPermissionsRepository(
                                    this@MainMapFragment,
                                    requireContext().applicationContext
                                )
                            )
                        )

                        observePermissionsDeniedResult.onEach {
                            showPermissionsDeniedError()
                        }.launchIn(lifecycleScope)

                        onInitFinish()
                    }
        }
    }

    private fun showPermissionsDeniedError() {
        if (context != null) {
            Toast.makeText(
                context,
                getString(R.string.permissions_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}