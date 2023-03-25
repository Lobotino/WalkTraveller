package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState

class FirstWelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var userInfoRepository: IUserInfoRepository

    private var isPrivacyPolicyChecked = false

    private val continueButtonState = MutableStateFlow(
        WelcomeContinueButtonState.DEFAULT
    )

    val observeContinueButtonStateChanges: Flow<WelcomeContinueButtonState> = continueButtonState

    lateinit var onContinueListener: () -> Unit

    fun setUserInfoRepository(userInfoRepository: IUserInfoRepository) {
        this.userInfoRepository = userInfoRepository
    }

    fun onContinueButtonClick() {
        if (isPrivacyPolicyChecked) {
            onContinueListener()
            userInfoRepository.setWelcomeTutorialFinished(true)
        } else {
            continueButtonState.tryEmit(WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST)
        }
    }

    fun onPrivacyPolicyCheckedChanged(isPrivacyPolicyChecked: Boolean) {
        this.isPrivacyPolicyChecked = isPrivacyPolicyChecked
        if (isPrivacyPolicyChecked && continueButtonState.value == WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST) {
            continueButtonState.tryEmit(WelcomeContinueButtonState.DEFAULT)
        }
    }
}