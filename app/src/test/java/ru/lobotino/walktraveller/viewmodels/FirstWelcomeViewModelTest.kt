package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.ui.model.WelcomeContinueButtonState

class FirstWelcomeViewModelTest {

    private lateinit var userInfoRepository: IUserInfoRepository
    private lateinit var sut: FirstWelcomeViewModel
    private var continueCalled = false

    @Before
    fun setUp() {
        continueCalled = false
        userInfoRepository = mockk(relaxed = true)
        sut = FirstWelcomeViewModel(mockk<Application>(relaxed = true)).apply {
            setUserInfoRepository(userInfoRepository)
            onContinueListener = { continueCalled = true }
        }
    }

    @After
    fun tearDown() {
        continueCalled = false
    }

    @Test
    fun continueBlockedWhenPolicyNotChecked() = runTest {
        // when
        sut.onContinueButtonClick()

        // then
        assertFalse(continueCalled)
        verify(exactly = 0) { userInfoRepository.setWelcomeTutorialFinished(any()) }
        assertEquals(
            WelcomeContinueButtonState.NEED_TO_AGREEMENT_FIRST,
            sut.observeContinueButtonStateChanges.first(),
        )
    }

    @Test
    fun continueProceedsWhenPolicyChecked() = runTest {
        // given
        sut.onPrivacyPolicyCheckedChanged(true)

        // when
        sut.onContinueButtonClick()

        // then
        assertTrue(continueCalled)
        verify(exactly = 1) { userInfoRepository.setWelcomeTutorialFinished(true) }
    }

    @Test
    fun buttonReturnsToDefaultAfterCheckingPolicy() = runTest {
        // given
        sut.onContinueButtonClick() // moves to NEED_TO_AGREEMENT_FIRST

        // when
        sut.onPrivacyPolicyCheckedChanged(true)

        // then
        assertEquals(
            WelcomeContinueButtonState.DEFAULT,
            sut.observeContinueButtonStateChanges.first(),
        )
    }
}
