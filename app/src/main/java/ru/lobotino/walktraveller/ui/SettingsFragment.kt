package ru.lobotino.walktraveller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toolbar
import androidx.fragment.app.Fragment
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.utils.ext.openNavigationMenu

class SettingsFragment : Fragment() {

    private lateinit var toolbar: Toolbar

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
    }
}