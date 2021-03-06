package io.github.ricardorlg.devicefarm.tractor.stubs

import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging

class MockedDeviceFarmLogging(private val printMessages: Boolean = false) : IDeviceFarmTractorLogging {
    override fun logMessage(msg: String) {
        if (printMessages)
            println(msg)
    }

    override fun logError(error: Throwable?, msg: String) {
        if (printMessages) {
            System.err.println(msg)
        }
    }
}