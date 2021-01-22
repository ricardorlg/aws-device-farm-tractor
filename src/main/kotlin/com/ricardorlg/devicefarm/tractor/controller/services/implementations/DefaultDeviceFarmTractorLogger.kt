package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import mu.KLogger
import mu.KotlinLogging

class DefaultDeviceFarmTractorLogger(
    loggerName: String,
) : IDeviceFarmTractorLogging {
    private val logger: KLogger by lazy { KotlinLogging.logger(loggerName) }
    override fun logStatus(msg: String) {
        kotlin.runCatching {
            logger
                .info(msg)
        }.onFailure {
            println(msg)
        }
    }

    override fun logError(error: Throwable?, msg: String) {
        kotlin.runCatching {
            logger.error(error) { msg }
        }.onFailure {
            System.err.println(msg)
        }
    }
}