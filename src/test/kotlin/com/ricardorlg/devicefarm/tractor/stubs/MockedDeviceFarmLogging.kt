package com.ricardorlg.devicefarm.tractor.stubs

import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging

class MockedDeviceFarmLogging(private val printMessages: Boolean = false) : IDeviceFarmTractorLogging {
    override fun logStatus(msg: String) {
        if (printMessages)
            println(msg)
    }

    override fun logError(error: Throwable?, msg: String) {
        if (printMessages) {
            System.err.println(msg)
        }
    }
}