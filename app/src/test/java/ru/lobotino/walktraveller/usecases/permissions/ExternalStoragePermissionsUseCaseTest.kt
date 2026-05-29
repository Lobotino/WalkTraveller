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
import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository

class ExternalStoragePermissionsUseCaseTest {

    private lateinit var permissionsRepository: IPermissionsRepository
    private lateinit var sut: ExternalStoragePermissionsUseCase

    @Before
    fun setUp() {
        permissionsRepository = mockk()
        sut = ExternalStoragePermissionsUseCase(permissionsRepository)
    }

    @Test
    fun `requestPermissions forwards both callbacks to repository`() {
        // given
        val allGranted: () -> Unit = {}
        val someDenied: (List<String>) -> Unit = {}
        val allGrantedSlot = slot<() -> Unit>()
        val someDeniedSlot = slot<(List<String>) -> Unit>()
        every {
            permissionsRepository.requestPermissions(capture(allGrantedSlot), capture(someDeniedSlot))
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
        every { permissionsRepository.isPermissionsGranted() } returns true

        // when
        val result = sut.isPermissionsGranted()

        // then
        assertEquals(true, result)
    }
}
