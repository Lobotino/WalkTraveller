package ru.lobotino.walktraveller.ui.adapter

import android.app.Dialog
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.ui.dialog.PrivacyPolicyDialog
import ru.lobotino.walktraveller.ui.dialog.TermsOfUseDialog
import ru.lobotino.walktraveller.ui.model.WelcomePage
import ru.lobotino.walktraveller.ui.view.TrackRecordingAnimationView

/**
 * Pages for the first-launch onboarding pager: tutorial slides (animated track)
 * and a final consent slide. Animations start/stop with page attach/detach.
 */
class WelcomeSlidesAdapter(
    private val pages: List<WelcomePage>,
    private val onPrivacyCheckedChange: (Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_TUTORIAL = 0
        const val TYPE_CONSENT = 1
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = when (pages[position]) {
        is WelcomePage.Tutorial -> TYPE_TUTORIAL
        WelcomePage.Consent -> TYPE_CONSENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TUTORIAL) {
            TutorialViewHolder(inflater.inflate(R.layout.item_welcome_tutorial, parent, false))
        } else {
            ConsentViewHolder(
                inflater.inflate(R.layout.item_welcome_consent, parent, false),
                onPrivacyCheckedChange,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val page = pages[position]) {
            is WelcomePage.Tutorial -> (holder as TutorialViewHolder).bind(page)
            WelcomePage.Consent -> (holder as ConsentViewHolder).bind()
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is TutorialViewHolder) holder.animationView.startAnimation()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is TutorialViewHolder) holder.animationView.stopAnimation()
    }

    class TutorialViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val animationView: TrackRecordingAnimationView =
            view.findViewById(R.id.welcome_animation_view)
        private val title: TextView = view.findViewById(R.id.welcome_slide_title)
        private val subtitle: TextView = view.findViewById(R.id.welcome_slide_subtitle)

        fun bind(page: WelcomePage.Tutorial) {
            animationView.mode = page.mode
            title.setText(page.titleRes)
            subtitle.setText(page.subtitleRes)
        }
    }

    class ConsentViewHolder(
        view: View,
        private val onPrivacyCheckedChange: (Boolean) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.privacy_policy_check_box)
        private val policyText: TextView = view.findViewById(R.id.privacy_policy_text)

        fun bind() {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                onPrivacyCheckedChange(isChecked)
            }
            bindPolicyText()
        }

        private fun bindPolicyText() {
            val context = policyText.context
            val full = context.getString(R.string.welcome_privacy_policy_accepting)
            val policyPart = context.getString(R.string.welcome_privacy_policy_text_part)
            val termsPart = context.getString(R.string.welcome_terms_of_use_text_part)
            val policyStart = full.indexOf(policyPart)
            val termsStart = full.indexOf(termsPart)

            policyText.text = SpannableString(full).apply {
                if (policyStart >= 0) {
                    setSpan(
                        clickableSpan {
                            PrivacyPolicyDialog(context).showFullScreen()
                        },
                        policyStart,
                        policyStart + policyPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                if (termsStart >= 0) {
                    setSpan(
                        clickableSpan {
                            TermsOfUseDialog(context).showFullScreen()
                        },
                        termsStart,
                        termsStart + termsPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            policyText.movementMethod = LinkMovementMethod.getInstance()
        }

        private fun clickableSpan(onClick: () -> Unit): ClickableSpan =
            object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            }
    }
}

private fun Dialog.showFullScreen() {
    show()
    window?.setLayout(MATCH_PARENT, MATCH_PARENT)
}
