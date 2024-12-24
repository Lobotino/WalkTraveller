package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.repositories.interfaces.IVibrationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.interfaces.IFinishPathWritingUseCase

class FinishPathWritingUseCase(
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val vibrationRepository: IVibrationRepository,
) : IFinishPathWritingUseCase {

    override fun finishPathWriting() {
        writingPathStatesRepository.setWritingPathNow(false)
        vibrationRepository.vibrateTriple(FINISH_WRITING_PATH_VIBRATION_DURATION, DEFAULT_AMPLITUDE)
    }

    companion object {
        private const val FINISH_WRITING_PATH_VIBRATION_DURATION = 100L
        private const val DEFAULT_AMPLITUDE = -1
    }
}
