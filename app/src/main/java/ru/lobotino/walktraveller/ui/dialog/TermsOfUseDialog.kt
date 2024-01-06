package ru.lobotino.walktraveller.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Window
import android.widget.Button
import android.widget.TextView
import ru.lobotino.walktraveller.R

class TermsOfUseDialog(
    context: Context
) : Dialog(context) {

    private val termsOfUseTitle = context.getString(R.string.terms_of_use_title)
    private val termsOfUseText = context.getString(R.string.terms_of_use)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowSettings()
        setContentView(R.layout.long_info_dialog)
        setupCloseButton()
        setupTitle()
        setupText()
    }

    private fun setupWindowSettings() {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun setupCloseButton() {
        findViewById<Button>(R.id.close_button).apply {
            setOnClickListener {
                dismiss()
            }
        }
    }

    private fun setupTitle() {
        findViewById<TextView>(R.id.dialog_title).text = termsOfUseTitle
    }

    private fun setupText() {
        findViewById<TextView>(R.id.dialog_text).apply {
            text = Html.fromHtml(termsOfUseText, Html.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
