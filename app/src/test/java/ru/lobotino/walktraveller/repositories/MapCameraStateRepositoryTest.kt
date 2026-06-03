package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint

class MapCameraStateRepositoryTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var sut: MapCameraStateRepository

    @Before
    fun setUp() {
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        sut = MapCameraStateRepository(sharedPreferences)
    }

    @After
    fun tearDown() {
        // mockk state is per-instance; nothing global to clear
    }

    @Test
    fun `getLastCameraState returns full state when all three keys present`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "16.5"

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(10.5, 20.25), 16.5), result)
    }

    @Test
    fun `getLastCameraState falls back to default zoom when only point is stored`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns null

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(10.5, 20.25), 15.0), result)
    }

    @Test
    fun `getLastCameraState falls back to default zoom on unparseable zoom value`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "not-a-number"

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(10.5, 20.25), 15.0), result)
    }

    @Test
    fun `getLastCameraState returns null when latitude missing`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns null
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns "20.25"
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "16.0"

        // when
        val result = sut.getLastCameraState()

        // then
        assertNull(result)
    }

    @Test
    fun `getLastCameraState returns null when longitude missing`() {
        // given
        every { sharedPreferences.getString("last_seen_point_latitude", null) } returns "10.5"
        every { sharedPreferences.getString("last_seen_point_longitude", null) } returns null
        every { sharedPreferences.getString("last_seen_zoom", null) } returns "16.0"

        // when
        val result = sut.getLastCameraState()

        // then
        assertNull(result)
    }

    @Test
    fun `setLastCameraState writes all three keys`() {
        // given
        val latSlot = slot<String>()
        val lngSlot = slot<String>()
        val zoomSlot = slot<String>()
        every { editor.putString("last_seen_point_latitude", capture(latSlot)) } returns editor
        every { editor.putString("last_seen_point_longitude", capture(lngSlot)) } returns editor
        every { editor.putString("last_seen_zoom", capture(zoomSlot)) } returns editor

        // when
        sut.setLastCameraState(MapCameraState(MapPoint(1.5, 2.5), 17.25))

        // then
        assertEquals("1.5", latSlot.captured)
        assertEquals("2.5", lngSlot.captured)
        assertEquals("17.25", zoomSlot.captured)
        verify(exactly = 1) { editor.apply() }
    }
}
