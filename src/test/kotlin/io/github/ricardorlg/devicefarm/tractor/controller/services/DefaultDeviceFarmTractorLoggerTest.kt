package io.github.ricardorlg.devicefarm.tractor.controller.services

import io.github.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmTractorLogger
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.captureStandardErr
import io.kotest.extensions.system.captureStandardOut
import io.kotest.matchers.shouldBe
import io.mockk.*
import mu.KLogger
import mu.KotlinLogging

class DefaultDeviceFarmTractorLoggerTest : StringSpec({

    val loggerName = "Test Logger"

    beforeTest {
        mockkObject(KotlinLogging)
    }

    "When an error happens fetching the logger then info messages should be redirect to stdout" {
        //GIVEN
        val expectedMessage = "test message"
        every { KotlinLogging.logger(any<String>()) } throws RuntimeException("test error")

        //WHEN
        val response = captureStandardOut {
            DefaultDeviceFarmTractorLogger(loggerName).logMessage(expectedMessage)
        }.trim()

        //THEN
        response shouldBe expectedMessage

        verify { KotlinLogging.logger(loggerName) }
        confirmVerified(KotlinLogging)
    }

    "info messages should be logged using Kotlin Logging lib"{
        //GIVEN
        val logger = mockk<KLogger>()
        every { logger.info(any<String>()) } just runs
        every { KotlinLogging.logger(loggerName) } returns logger

        //WHEN
        DefaultDeviceFarmTractorLogger(loggerName).logMessage("No matters")

        //THEN
        verifySequence {
            KotlinLogging.logger(loggerName)
            logger.info("No matters")
        }
        confirmVerified(logger, KotlinLogging)
    }

    "When an error happens fetching the logger then error messages should be redirect to stderr" {
        //GIVEN
        val expectedMessage = "test message"
        every { KotlinLogging.logger(any<String>()) } throws RuntimeException("test error")

        //WHEN
        val response = captureStandardErr {
            DefaultDeviceFarmTractorLogger(loggerName).logError(msg = expectedMessage)
        }.trim()

        //THEN
        response shouldBe expectedMessage

        verify { KotlinLogging.logger(loggerName) }
        confirmVerified(KotlinLogging)
    }

    "error messages should be logged using Kotlin Logging lib"{
        //GIVEN
        val error = java.lang.RuntimeException("test error")
        val logger = mockk<KLogger>()
        val slot = slot<() -> Any?>()
        every { logger.error(any<Throwable>(), capture(slot)) } just runs
        every { KotlinLogging.logger(loggerName) } returns logger

        //WHEN
        DefaultDeviceFarmTractorLogger(loggerName).logError(error, msg = "error message")

        //THEN
        slot.captured.invoke() shouldBe "error message"
        verifySequence {
            KotlinLogging.logger(loggerName)
            logger.error(error, slot.captured)
        }
        confirmVerified(logger, KotlinLogging)
    }

    afterTest {
        unmockkObject(KotlinLogging)
    }

})
