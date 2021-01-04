package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import mu.KotlinLogging

class DefaultDeviceFarmTractorLogger(
    private val loggerName: String,
) : IDeviceFarmTractorLogging {
    override fun logStatus(msg: String) {
        kotlin.runCatching {
            KotlinLogging
                .logger(loggerName)
                .info(msg)
        }.onFailure {
            println(msg)
        }
    }

    override fun logError(msg: String) {
        kotlin.runCatching {
            KotlinLogging
                .logger(loggerName)
                .error(msg)
        }.onFailure {
            System.err.println(msg)
        }
    }
}