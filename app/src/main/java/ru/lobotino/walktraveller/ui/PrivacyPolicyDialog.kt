package ru.lobotino.walktraveller.ui

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Window
import android.widget.Button
import android.widget.TextView
import ru.lobotino.walktraveller.R

class PrivacyPolicyDialog(
    context: Context
) : Dialog(context) {

    private val privacyPolicyTitle = context.getString(R.string.privacy_policy_title)
    private val privacyPolicyText = context.getString(R.string.privacy_policy)

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
        findViewById<TextView>(R.id.dialog_title).text = privacyPolicyTitle
    }

    @Suppress("DEPRECATION")
    private fun setupText() {
        findViewById<TextView>(R.id.dialog_text).apply {
            text =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(privacyPolicyText, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    Html.fromHtml(privacyPolicyText)
                }

            movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
