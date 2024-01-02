package ru.lobotino.walktraveller.ui

import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.AppScreen
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState
import ru.lobotino.walktraveller.utils.ext.navigateTo
import ru.lobotino.walktraveller.viewmodels.FirstWelcomeViewModel

class FirstWelcomeFragment : Fragment() {

    private lateinit var continueButton: Button
    private lateinit var privacyPolicyCheckBox: CheckBox
    private lateinit var privacyPolicyText: TextView

    private var viewModel: FirstWelcomeViewModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.first_welcome_fragment,
            container,
            false
        ).also { view ->
            initViews(view)
            initViewModel()
        }
    }

    private fun initViews(view: View) {
        continueButton = view.findViewById<Button>(R.id.continue_button).apply {
            setOnClickListener {
                viewModel?.onContinueButtonClick()
            }
        }
        privacyPolicyCheckBox = view.findViewById<CheckBox>(R.id.privacy_policy_check_box).apply {
            setOnCheckedChangeListener { _, isChecked ->
                viewModel?.onPrivacyPolicyCheckedChanged(isChecked)
            }
        }

        privacyPolicyText = view.findViewById<TextView?>(R.id.privacy_policy_text).apply {
            val privacyPolicyTextFull = context.getString(R.string.welcome_privacy_policy_accepting)
            val privacyPolicyTextPart = context.getString(R.string.welcome_privacy_policy_text_part)
            val termsOfUseTextPart = context.getString(R.string.welcome_terms_of_use_text_part)

            val privacyPolicyStartIndex = privacyPolicyTextFull.indexOf(privacyPolicyTextPart)
            val termsOfUseStartIndex = privacyPolicyTextFull.indexOf(termsOfUseTextPart)

            text =
                SpannableString(privacyPolicyTextFull).apply {
                    setSpan(
                        object : ClickableSpan() {
                            override fun onClick(textView: View) {
                                context?.let { context ->
                                    PrivacyPolicyDialog(context).apply {
                                        show()
                                        window?.setLayout(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                super.updateDrawState(ds)
                                ds.isUnderlineText = false
                            }
                        },
                        privacyPolicyStartIndex,
                        privacyPolicyStartIndex + privacyPolicyTextPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    setSpan(
                        object : ClickableSpan() {
                            override fun onClick(textView: View) {
                                context?.let { context ->
                                    TermsOfUseDialog(context).apply {
                                        show()
                                        window?.setLayout(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                super.updateDrawState(ds)
                                ds.isUnderlineText = false
                            }
                        },
                        termsOfUseStartIndex,
                        termsOfUseStartIndex + termsOfUseTextPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun initViewModel() {
        viewModel =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                .create(FirstWelcomeViewModel::class.java).apply {
                    setUserInfoRepository(
                        UserInfoRepository(
                            requireContext().getSharedPreferences(
                                App.SHARED_PREFS_TAG,
                                AppCompatActivity.MODE_PRIVATE
                            )
                        )
                    )

                    observeContinueButtonStateChanges.onEach { uiState ->
                        updateContinueButtonState(uiState)
                    }.launchIn(lifecycleScope)

                    onContinueListener = {
                        navigateTo(AppScreen.MAP_SCREEN, arguments?.getParcelable(EXTRA_DATA_URI))
                    }
                }
    }

    private fun updateContinueButtonState(buttonState: WelcomeContinueButtonState) {
        when (buttonState) {
            WelcomeContinueButtonState.DEFAULT -> {
                continueButton.isEnabled = true
                continueButton.text =
                    getString(R.string.welcome_continue_button_default_text)
            }

            WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST -> {
                continueButton.isEnabled = false
                continueButton.text =
                    getString(R.string.welcome_continue_button_need_to_agreement_first)
            }
        }
    }

    companion object {
        private const val EXTRA_DATA_URI = "EXTRA_DATA_URI"

        fun newInstance(extraData: Uri?): FirstWelcomeFragment {
            return FirstWelcomeFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA_URI, extraData)
                }
            }
        }
    }
}
