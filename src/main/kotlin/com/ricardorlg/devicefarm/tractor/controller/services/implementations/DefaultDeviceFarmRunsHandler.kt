package com.ricardorlg.devicefarm.tractor.controller.services.implementations

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmRunsHandler
import com.ricardorlg.devicefarm.tractor.model.*
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*

class DefaultDeviceFarmRunsHandler(private val deviceFarmClient: DeviceFarmClient) : IDeviceFarmRunsHandler {

    override suspend fun scheduleRun(
        appArn: String,
        runConfiguration: ScheduleRunConfiguration,
        devicePoolArn: String,
        executionConfiguration: ExecutionConfiguration,
        runName: String,
        projectArn: String,
        testConfiguration: ScheduleRunTest
    ): Either<DeviceFarmTractorError, Run> {
        return when {
            appArn.isBlank() -> {
                Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_APP_ARN))
            }
            devicePoolArn.isBlank() -> {
                Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_DEVICE_POOL_ARN))
            }
            projectArn.isBlank() -> {
                Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_PROJECT_ARN))
            }
            else -> {
                Either.catch {
                    val request = ScheduleRunRequest
                        .builder()
                        .appArn(appArn)
                        .configuration(runConfiguration)
                        .devicePoolArn(devicePoolArn)
                        .executionConfiguration(executionConfiguration)
                        .projectArn(projectArn)
                        .test(testConfiguration)
                        .apply {
                            if (runName.isNotBlank())
                                name(runName)
                        }.build()
                    deviceFarmClient
                        .scheduleRun(request)
                        .run()
                }.mapLeft {
                    ErrorSchedulingRun(ERROR_SCHEDULING_RUN, it)
                }
            }
        }
    }

    override suspend fun fetchRun(runArn: String): Either<DeviceFarmTractorError, Run> {
        return if (runArn.isBlank()) {
            Either.left(DeviceFarmTractorErrorIllegalArgumentException(EMPTY_RUN_ARN))
        } else {
            Either.catch {
                deviceFarmClient
                    .getRun(
                        GetRunRequest
                            .builder()
                            .arn(runArn)
                            .build()
                    ).run()
            }.mapLeft {
                ErrorFetchingAWSRun(ERROR_FETCHING_RUN.format(runArn), it)
            }
        }
    }

    override suspend fun getAssociatedJobs(run: Run): Either<DeviceFarmTractorError, List<Job>> {
        return Either.catch {
            deviceFarmClient
                .listJobsPaginator(
                    ListJobsRequest
                        .builder()
                        .arn(run.arn())
                        .build()
                ).jobs()
                .toList()
        }.mapLeft {
            ErrorListingAssociatedJobs(ERROR_FETCHING_JOBS.format(run.arn()), it)
        }
    }
}