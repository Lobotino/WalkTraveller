package ru.lobotino.walktraveller.usecases.permissions

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.repositories.permissions.NotificationsPermissionsRepository

class NotificationsPermissionsUseCaseTest {

    private lateinit var notificationsPermissionsRepository: NotificationsPermissionsRepository
    private lateinit var sut: NotificationsPermissionsUseCase

    @Before
    fun setUp() {
        notificationsPermissionsRepository = mockk()
        sut = NotificationsPermissionsUseCase(notificationsPermissionsRepository)
    }

    @Test
    fun `requestPermissions forwards both callbacks to repository`() {
        // given
        val allGranted: () -> Unit = {}
        val someDenied: (List<String>) -> Unit = {}
        val allGrantedSlot = slot<() -> Unit>()
        val someDeniedSlot = slot<(List<String>) -> Unit>()
        every {
            notificationsPermissionsRepository.requestPermissions(
                capture(allGrantedSlot),
                capture(someDeniedSlot)
            )
        } just Runs

        // when
        sut.requestPermissions(allGranted, someDenied)

        // then
        assertSame(allGranted, allGrantedSlot.captured)
        assertSame(someDenied, someDeniedSlot.captured)
    }

    @Test
    fun `isPermissionsGranted returns repository value`() {
        // given
        every { notificationsPermissionsRepository.isPermissionsGranted() } returns true

        // when
        val result = sut.isPermissionsGranted()

        // then
        assertEquals(true, result)
    }
}
