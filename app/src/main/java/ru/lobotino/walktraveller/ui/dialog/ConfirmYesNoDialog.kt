package ru.lobotino.walktraveller.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import ru.lobotino.walktraveller.R

open class ConfirmYesNoDialog(
    context: Context,
    private val title: String,
    private val description: String,
    private val onYesClicked: (() -> Unit)?,
    private val onNoClicked: (() -> Unit)?
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        setupWindowSettings()
        setContentView(R.layout.yes_no_confirm_dialog)
        setupTitle()
        setupDescription()
        setupCallbacks()
    }

    protected fun setupWindowSettings() {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    protected fun setupTitle() {
        findViewById<TextView>(R.id.dialog_title).text = title
    }

    protected fun setupDescription() {
        findViewById<TextView>(R.id.dialog_description).text = description
    }

    protected open fun setupCallbacks() {
        findViewById<Button>(R.id.yes_button).apply {
            setOnClickListener {
                onYesClicked?.invoke()
                dismiss()
            }
        }
        findViewById<Button>(R.id.no_button).apply {
            setOnClickListener {
                onNoClicked?.invoke()
                dismiss()
            }
        }
    }
}
