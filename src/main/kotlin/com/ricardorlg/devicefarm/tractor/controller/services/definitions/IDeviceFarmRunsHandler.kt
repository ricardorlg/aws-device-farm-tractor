package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import software.amazon.awssdk.services.devicefarm.model.*

interface IDeviceFarmRunsHandler {

    suspend fun scheduleRun(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String = "",
        projectArn: String,
        testConfiguration: ScheduleRunTest
    ): Either<DeviceFarmTractorError, Run>

    suspend fun fetchRun(runArn: String): Either<DeviceFarmTractorError, Run>

    suspend fun getAssociatedJobs(run:Run):Either<DeviceFarmTractorError,List<Job>>
}