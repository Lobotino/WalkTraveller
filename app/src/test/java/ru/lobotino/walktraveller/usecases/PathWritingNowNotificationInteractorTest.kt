package ru.lobotino.walktraveller.usecases

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.repositories.interfaces.INotificationRepository

class PathWritingNowNotificationInteractorTest {

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationRepository: INotificationRepository
    private lateinit var notification: Notification
    private lateinit var sut: PathWritingNowNotificationInteractor

    @Before
    fun setUp() {
        notificationManager = mockk()
        notificationRepository = mockk()
        notification = mockk()
        every { notificationRepository.getNotificationId() } returns NOTIFICATION_ID
        every { notificationRepository.getNotification() } returns notification
        sut = PathWritingNowNotificationInteractor(notificationManager, notificationRepository)
    }

    @Test
    fun `showNotification posts notification with repository id and content`() {
        // given
        every { notificationManager.notify(any<Int>(), any<Notification>()) } just Runs

        // when
        sut.showNotification()

        // then
        verify(exactly = 1) { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    @Test
    fun `hideNotification cancels notification by repository id`() {
        // given
        every { notificationManager.cancel(any<Int>()) } just Runs

        // when
        sut.hideNotification()

        // then
        verify(exactly = 1) { notificationManager.cancel(NOTIFICATION_ID) }
    }

    @Test
    fun `getNotificationId returns repository id`() {
        // given

        // when
        val result = sut.getNotificationId()

        // then
        assertEquals(NOTIFICATION_ID, result)
    }

    @Test
    fun `getNotification returns repository notification`() {
        // given

        // when
        val result = sut.getNotification()

        // then
        assertSame(notification, result)
    }

    @Test
    fun `isNotificationShowingNow is true when an active notification matches id`() {
        // given
        val activeNotification = mockk<StatusBarNotification>()
        every { activeNotification.id } returns NOTIFICATION_ID
        every { notificationManager.activeNotifications } returns arrayOf(activeNotification)

        // when
        val result = sut.isNotificationShowingNow()

        // then
        assertTrue(result)
    }

    @Test
    fun `isNotificationShowingNow is false when no active notifications`() {
        // given
        every { notificationManager.activeNotifications } returns emptyArray()

        // when
        val result = sut.isNotificationShowingNow()

        // then
        assertFalse(result)
    }

    private companion object {
        const val NOTIFICATION_ID = 7
    }
}
