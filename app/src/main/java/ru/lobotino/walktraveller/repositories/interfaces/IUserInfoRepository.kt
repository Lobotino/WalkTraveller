package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.ui.model.WelcomeTutorialStep

interface IUserInfoRepository {

    fun saveUserId(id: String)

    fun getUserId(): String?

    fun saveUserName(name: String)

    fun getUserName(): String?

    fun setWelcomeTutorialStep(tutorialStep: WelcomeTutorialStep)

    fun getWelcomeTutorialStep(): WelcomeTutorialStep

    fun setWelcomeTutorialFinished(isFinished: Boolean)

    fun isWelcomeTutorialFinished(): Boolean
}
