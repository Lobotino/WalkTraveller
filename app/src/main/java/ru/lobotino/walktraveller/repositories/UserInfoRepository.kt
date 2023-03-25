package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.ui.model.WelcomeTutorialStep

class UserInfoRepository(private val sharedPreferences: SharedPreferences) : IUserInfoRepository {

    companion object {
        private const val USER_ID_PREFERENCE = "user_id"
        private const val USER_NAME_PREFERENCE = "user_name"
        private const val WELCOME_TUTORIAL_STEP_PREFERENCE = "welcome_tutorial_step"
        private const val IS_WELCOME_TUTORIAL_FINISHED_PREFERENCE = "is_welcome_tutorial_finished"
    }

    override fun saveUserId(id: String) {
        sharedPreferences.edit().apply {
            putString(USER_ID_PREFERENCE, id)
            apply()
        }
    }

    override fun getUserId(): String? {
        return sharedPreferences.getString(USER_NAME_PREFERENCE, null)
    }

    override fun saveUserName(name: String) {
        sharedPreferences.edit().apply {
            putString(USER_NAME_PREFERENCE, name)
            apply()
        }
    }

    override fun getUserName(): String? {
        return sharedPreferences.getString(USER_NAME_PREFERENCE, null)
    }

    override fun setWelcomeTutorialFinished(isFinished: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(IS_WELCOME_TUTORIAL_FINISHED_PREFERENCE, isFinished)
            apply()
        }
    }

    override fun isWelcomeTutorialFinished(): Boolean {
        return sharedPreferences.getBoolean(IS_WELCOME_TUTORIAL_FINISHED_PREFERENCE, false)
    }

    override fun setWelcomeTutorialStep(tutorialStep: WelcomeTutorialStep) {
        sharedPreferences.edit().apply {
            putInt(WELCOME_TUTORIAL_STEP_PREFERENCE, tutorialStep.ordinal)
            apply()
        }
    }

    override fun getWelcomeTutorialStep(): WelcomeTutorialStep {
        return WelcomeTutorialStep.values()[sharedPreferences.getInt(
            WELCOME_TUTORIAL_STEP_PREFERENCE,
            WelcomeTutorialStep.PRIVACY_POLICY.ordinal
        )]
    }
}