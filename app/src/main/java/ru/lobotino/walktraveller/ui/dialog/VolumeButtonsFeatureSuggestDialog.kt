package ru.lobotino.walktraveller.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import ru.lobotino.walktraveller.R

class VolumeButtonsFeatureSuggestDialog(context: Context, onYesClicked: () -> Unit, onNoClicked: () -> Unit) : ConfirmYesNoDialog(
    context,
    context.getString(R.string.volume_buttons_feature_request_title),
    context.getString(R.string.volume_buttons_feature_request_desc),
    onYesClicked,
    onNoClicked
) {

    private lateinit var dropdownListButton: ImageView
    private lateinit var howItWorksLayout: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        setupWindowSettings()
        setContentView(R.layout.volume_buttons_suggest_confirm_dialog)
        setupTitle()
        setupDescription()
        setupCallbacks()
        dropdownListButton = findViewById(R.id.dropdown_list_icon)
    }

    override fun setupCallbacks() {
        super.setupCallbacks()
        findViewById<ViewGroup>(R.id.how_it_works_button).apply {
            setOnClickListener {
                //TODO
            }
        }
    }
}