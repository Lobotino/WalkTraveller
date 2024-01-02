package ru.lobotino.walktraveller.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import ru.lobotino.walktraveller.R

class DeleteConfirmDialog(
    context: Context,
    private val onCancel: (() -> Unit)? = null,
    private val onConfirm: (() -> Unit)?
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowSettings()
        setContentView(R.layout.delete_confirm_dialog)
        setupCallbacks()
        setupTitle()
        setupDescription()
    }

    private fun setupWindowSettings() {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun setupCallbacks() {
        findViewById<Button>(R.id.confirm_button).apply {
            setOnClickListener {
                onConfirm?.invoke()
                dismiss()
            }
        }
        findViewById<Button>(R.id.cancel_button).apply {
            setOnClickListener {
                onCancel?.invoke()
                dismiss()
            }
        }
    }

    private fun setupTitle() {
        findViewById<TextView>(R.id.delete_confirm_title).text =
            context.getString(R.string.delete_path_confirm_title)
    }

    private fun setupDescription() {
        findViewById<TextView>(R.id.delete_confirm_description).text =
            context.getString(R.string.delete_path_confirm_description)
    }
}
