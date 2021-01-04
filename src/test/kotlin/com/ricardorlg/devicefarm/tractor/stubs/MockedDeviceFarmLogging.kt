package com.ricardorlg.devicefarm.tractor.stubs

import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging

class MockedDeviceFarmLogging : IDeviceFarmTractorLogging {
    override fun logStatus(msg: String) {}

    override fun logError(msg: String) {}
}