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
import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository

class GeoPermissionsUseCaseTest {

    private lateinit var geoPermissionsRepository: GeoPermissionsRepository
    private lateinit var sut: GeoPermissionsUseCase

    @Before
    fun setUp() {
        geoPermissionsRepository = mockk()
        sut = GeoPermissionsUseCase(geoPermissionsRepository)
    }

    @Test
    fun `requestPermissions forwards both callbacks to repository`() {
        // given
        val allGranted: () -> Unit = {}
        val someDenied: (List<String>) -> Unit = {}
        val allGrantedSlot = slot<() -> Unit>()
        val someDeniedSlot = slot<(List<String>) -> Unit>()
        every {
            geoPermissionsRepository.requestPermissions(capture(allGrantedSlot), capture(someDeniedSlot))
        } just Runs

        // when
        sut.requestPermissions(allGranted, someDenied)

        // then
        assertSame(allGranted, allGrantedSlot.captured)
        assertSame(someDenied, someDeniedSlot.captured)
    }

    @Test
    fun `isGeoPermissionsGranted returns repository value`() {
        // given
        every { geoPermissionsRepository.isGeoPermissionsGranted() } returns false

        // when
        val result = sut.isGeoPermissionsGranted()

        // then
        assertEquals(false, result)
    }
}
