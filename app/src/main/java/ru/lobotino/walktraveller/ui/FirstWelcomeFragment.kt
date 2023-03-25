package ru.lobotino.walktraveller.ui

import android.os.Bundle
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
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState
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
            //TODO
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
                        activity?.let { activity ->
                            if (activity is MainActivity) {
                                activity.onFinishWelcomeScreen()
                            }
                        }
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
}