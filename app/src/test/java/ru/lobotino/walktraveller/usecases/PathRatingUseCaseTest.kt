package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IVibrationRepository

class PathRatingUseCaseTest {

    private lateinit var pathRatingRepository: IPathRatingRepository
    private lateinit var vibrationRepository: IVibrationRepository
    private lateinit var sut: PathRatingUseCase

    @Before
    fun setUp() {
        pathRatingRepository = mockk()
        vibrationRepository = mockk()
        every { pathRatingRepository.setCurrentRating(any()) } just Runs
        every { vibrationRepository.vibrate(any(), any()) } just Runs
        every { vibrationRepository.vibrateDouble(any(), any()) } just Runs
        sut = PathRatingUseCase(pathRatingRepository, vibrationRepository)
    }

    @Test
    fun `setCurrentRating stores the rating in repository`() {
        // given
        val rating = SegmentRating.GOOD

        // when
        sut.setCurrentRating(rating)

        // then
        verify(exactly = 1) { pathRatingRepository.setCurrentRating(rating) }
    }

    @Test
    fun `setCurrentRating NORMAL triggers single vibration`() {
        // given

        // when
        sut.setCurrentRating(SegmentRating.NORMAL)

        // then
        verify(exactly = 1) { vibrationRepository.vibrate(100L, -1) }
    }

    @Test
    fun `setCurrentRating GOOD triggers single vibration`() {
        // given

        // when
        sut.setCurrentRating(SegmentRating.GOOD)

        // then
        verify(exactly = 1) { vibrationRepository.vibrate(100L, -1) }
    }

    @Test
    fun `setCurrentRating NONE triggers single vibration`() {
        // given

        // when
        sut.setCurrentRating(SegmentRating.NONE)

        // then
        verify(exactly = 1) { vibrationRepository.vibrate(100L, -1) }
    }

    @Test
    fun `setCurrentRating BADLY triggers double vibration`() {
        // given

        // when
        sut.setCurrentRating(SegmentRating.BADLY)

        // then
        verify(exactly = 1) { vibrationRepository.vibrateDouble(100L, -1) }
    }

    @Test
    fun `setCurrentRating PERFECT triggers double vibration`() {
        // given

        // when
        sut.setCurrentRating(SegmentRating.PERFECT)

        // then
        verify(exactly = 1) { vibrationRepository.vibrateDouble(100L, -1) }
    }

    @Test
    fun `getCurrentRating returns repository value`() {
        // given
        every { pathRatingRepository.getCurrentRating() } returns SegmentRating.PERFECT

        // when
        val result = sut.getCurrentRating()

        // then
        assertEquals(SegmentRating.PERFECT, result)
    }
}
