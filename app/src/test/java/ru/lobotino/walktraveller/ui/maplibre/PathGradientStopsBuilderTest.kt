package ru.lobotino.walktraveller.ui.maplibre

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapPoint

class PathGradientStopsBuilderTest {

    @Test
    fun `haversineMeters returns zero for identical points`() {
        // given
        val point = MapPoint(latitude = 55.7558, longitude = 37.6173)

        // when
        val result = haversineMeters(point, point)

        // then
        assertEquals(0.0, result, 1e-6)
    }

    @Test
    fun `haversineMeters one degree of longitude at equator is about 111_320 meters`() {
        // given
        val a = MapPoint(latitude = 0.0, longitude = 0.0)
        val b = MapPoint(latitude = 0.0, longitude = 1.0)

        // when
        val result = haversineMeters(a, b)

        // then
        assertEquals(111_320.0, result, 111_320.0 * 0.01)
    }

    @Test
    fun `haversineMeters one degree of latitude is about 111_320 meters`() {
        // given
        val a = MapPoint(latitude = 55.0, longitude = 37.0)
        val b = MapPoint(latitude = 56.0, longitude = 37.0)

        // when
        val result = haversineMeters(a, b)

        // then
        assertEquals(111_320.0, result, 111_320.0 * 0.01)
    }
}
