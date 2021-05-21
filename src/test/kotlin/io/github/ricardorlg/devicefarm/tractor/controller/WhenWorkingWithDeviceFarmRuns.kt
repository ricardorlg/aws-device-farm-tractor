package io.github.ricardorlg.devicefarm.tractor.controller

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import io.github.ricardorlg.devicefarm.tractor.stubs.*
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import software.amazon.awssdk.services.devicefarm.model.*
import kotlin.time.Duration
import kotlin.time.milliseconds

class WhenWorkingWithDeviceFarmRuns : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val deviceFarmProjectsHandler = MockedDeviceFarmProjectsHandler()
    val devicePoolsHandler = MockedDeviceFarmDevicePoolsHandler()
    val uploadArtifactsHandler = MockedDeviceFarmUploadArtifactsHandler()
    val artifactsHandler = MockedDeviceFarmArtifactsHandler()
    val appArn = "arn:app:test:1234"
    val runConfiguration = ScheduleRunConfiguration.builder().build()
    val devicePoolArn = "arn:device_pool:test:1234"
    val executionConfiguration = ExecutionConfiguration.builder().build()
    val projectArn = "arn:project:test:1234"
    val testConfiguration = ScheduleRunTest.builder().build()
    val runArn = "arn:run:test"
    val runName = "test_run_name"

    "It should return the executed run when the execution is completed"{
        //GIVEN
        val initialRun = Run
            .builder()
            .name(runName)
            .arn(runArn)
            .status(ExecutionStatus.SCHEDULING)
            .build()

        val expectedResult = Run
            .builder()
            .name(runName)
            .arn(runArn)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val runScheduleHandler = MockedDeviceFarmRunsHandler(
            scheduleRunImpl = { Either.Right(initialRun) },
            fetchRunImpl = { Either.Right(expectedResult) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).scheduleRunAndWait(
            appArn = appArn,
            runConfiguration = runConfiguration,
            devicePoolArn = devicePoolArn,
            executionConfiguration = executionConfiguration,
            runName = runName,
            projectArn = projectArn,
            testConfiguration = testConfiguration
        )

        //THEN
        response shouldBeRight expectedResult

    }

    "It should wait until the execution completes"{
        //GIVEN
        val initialRun = Run
            .builder()
            .name(runName)
            .arn(runArn)
            .status(ExecutionStatus.SCHEDULING)
            .build()

        val expectedResult = Run
            .builder()
            .name(runName)
            .arn(runArn)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val responses = iterator {
            yield(initialRun)
            yield(initialRun.copy { it.status(ExecutionStatus.PREPARING) })
            yield(initialRun.copy { it.status(ExecutionStatus.RUNNING) })
            yield(expectedResult)
        }

        val runScheduleHandler = MockedDeviceFarmRunsHandler(
            scheduleRunImpl = { Either.Right(initialRun) },
            fetchRunImpl = { Either.Right(responses.next()) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).scheduleRunAndWait(
            appArn = appArn,
            runConfiguration = runConfiguration,
            devicePoolArn = devicePoolArn,
            executionConfiguration = executionConfiguration,
            runName = runName,
            projectArn = projectArn,
            testConfiguration = testConfiguration,
            delaySpaceInterval = Duration.milliseconds(5)
        )

        //THEN
        response shouldBeRight expectedResult

    }

    "It should return a DeviceFarmTractorError when fetching current run fails"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))

        val initialRun = Run
            .builder()
            .name(runName)
            .arn(runArn)
            .status(ExecutionStatus.SCHEDULING)
            .build()

        val runScheduleHandler = MockedDeviceFarmRunsHandler(
            scheduleRunImpl = { Either.Right(initialRun) },
            fetchRunImpl = { Either.Left(expectedError) }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).scheduleRunAndWait(
            appArn = appArn,
            runConfiguration = runConfiguration,
            devicePoolArn = devicePoolArn,
            executionConfiguration = executionConfiguration,
            runName = runName,
            projectArn = projectArn,
            testConfiguration = testConfiguration
        )

        //THEN
        response shouldBeLeft expectedError

    }

    "It should return a DeviceFarmTractorError when scheduling the run fails"{
        //GIVEN
        val expectedError = DeviceFarmTractorGeneralError(RuntimeException("test error"))

        val runScheduleHandler = MockedDeviceFarmRunsHandler(
            scheduleRunImpl = { Either.Left(expectedError) },
            fetchRunImpl = { fail("this should never been called") }
        )

        //WHEN
        val response = DefaultDeviceFarmTractorController(
            logger,
            deviceFarmProjectsHandler,
            devicePoolsHandler,
            uploadArtifactsHandler,
            runScheduleHandler,
            artifactsHandler
        ).scheduleRunAndWait(
            appArn = appArn,
            runConfiguration = runConfiguration,
            devicePoolArn = devicePoolArn,
            executionConfiguration = executionConfiguration,
            runName = runName,
            projectArn = projectArn,
            testConfiguration = testConfiguration
        )

        //THEN
        response shouldBeLeft expectedError

    }
})