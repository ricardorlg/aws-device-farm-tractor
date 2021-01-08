package com.ricardorlg.devicefarm.tractor.stubs

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmRunsHandler
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import io.kotest.assertions.fail
import software.amazon.awssdk.services.devicefarm.model.*

class MockedDeviceFarmRunsHandler(
    private val scheduleRunImpl: () -> Either<DeviceFarmTractorError, Run> = { fail("Not implemented") },
    private val fetchRunImpl: () -> Either<DeviceFarmTractorError, Run> = { fail("Not implemented") },
    private val getAssociatedJobsImpl: () -> Either<DeviceFarmTractorError, List<Job>> = { fail("Not implemented") }
) : IDeviceFarmRunsHandler {
    override suspend fun scheduleRun(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String,
        projectArn: String,
        testConfiguration: ScheduleRunTest
    ): Either<DeviceFarmTractorError, Run> {
        return scheduleRunImpl()
    }

    override suspend fun fetchRun(runArn: String): Either<DeviceFarmTractorError, Run> {
        return fetchRunImpl()
    }

    override suspend fun getAssociatedJobs(run: Run): Either<DeviceFarmTractorError, List<Job>> {
        return getAssociatedJobsImpl()
    }
}