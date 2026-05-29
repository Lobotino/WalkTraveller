package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.repositories.interfaces.IVibrationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository

class FinishPathWritingUseCaseTest {

    private lateinit var writingPathStatesRepository: IWritingPathStatesRepository
    private lateinit var vibrationRepository: IVibrationRepository
    private lateinit var sut: FinishPathWritingUseCase

    @Before
    fun setUp() {
        writingPathStatesRepository = mockk()
        vibrationRepository = mockk()
        every { writingPathStatesRepository.setWritingPathNow(any()) } just Runs
        every { vibrationRepository.vibrateTriple(any(), any()) } just Runs
        sut = FinishPathWritingUseCase(writingPathStatesRepository, vibrationRepository)
    }

    @Test
    fun `finishPathWriting marks writing state as stopped`() {
        // given

        // when
        sut.finishPathWriting()

        // then
        verify(exactly = 1) { writingPathStatesRepository.setWritingPathNow(false) }
    }

    @Test
    fun `finishPathWriting triggers triple vibration`() {
        // given

        // when
        sut.finishPathWriting()

        // then
        verify(exactly = 1) { vibrationRepository.vibrateTriple(100L, -1) }
    }
}
