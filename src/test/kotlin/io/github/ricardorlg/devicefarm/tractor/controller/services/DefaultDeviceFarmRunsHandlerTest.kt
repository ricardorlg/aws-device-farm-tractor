package io.github.ricardorlg.devicefarm.tractor.controller.services

import io.github.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmRunsHandler
import io.github.ricardorlg.devicefarm.tractor.model.*
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.filter
import io.mockk.*
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.model.*

class DefaultDeviceFarmRunsHandlerTest : StringSpec({

    val dfClient = mockk<DeviceFarmClient>()
    val appArn = "arn:app:test:1234"
    val runConfiguration = ScheduleRunConfiguration.builder().build()
    val devicePoolArn = "arn:device_pool:test:1234"
    val executionConfiguration = ExecutionConfiguration.builder().build()
    val projectArn = "arn:project:test:1234"
    val validTestTypes = listOf(TestType.APPIUM_NODE, TestType.APPIUM_WEB_NODE)
    val testNativeConfiguration = ScheduleRunTest.builder().type(TestType.APPIUM_NODE).build()
    val runARN = "arn:run:test"
    val runName = "test_run_name"

    "When scheduling a run, it should return the run as a right"{
        checkAll(Exhaustive.collection(validTestTypes)) { executionType ->
            //GIVEN
            val testExecutionConfiguration = ScheduleRunTest.builder().type(executionType).build()
            val expectedRun = Run
                .builder()
                .arn(runARN)
                .build()
            every {
                dfClient
                    .scheduleRun(any<ScheduleRunRequest>())
            } returns ScheduleRunResponse.builder().run(expectedRun).build()

            //WHEN
            val response = DefaultDeviceFarmRunsHandler(dfClient)
                .scheduleRun(
                    appArn = appArn,
                    runConfiguration = runConfiguration,
                    devicePoolArn = devicePoolArn,
                    executionConfiguration = executionConfiguration,
                    projectArn = projectArn,
                    testConfiguration = testExecutionConfiguration
                )

            //THEN
            response.shouldBeRight().shouldBe(expectedRun)
            verify {
                dfClient.scheduleRun(
                    ScheduleRunRequest
                        .builder()
                        .configuration(runConfiguration)
                        .devicePoolArn(devicePoolArn)
                        .executionConfiguration(executionConfiguration)
                        .projectArn(projectArn)
                        .test(testExecutionConfiguration)
                        .apply {
                            if (executionType == TestType.APPIUM_NODE)
                                appArn(appArn)
                        }
                        .build()
                )
            }

            confirmVerified(dfClient)
        }
    }

    "When test execution type is not supported it should return a DeviceFarmTractorErrorIllegalArgumentException as a left"{
        checkAll(Exhaustive.enum<TestType>().filter { it !in validTestTypes }) { executionType ->
            //GIVEN
            val testExecutionConfiguration = ScheduleRunTest.builder().type(executionType).build()

            //WHEN
            val response = DefaultDeviceFarmRunsHandler(dfClient)
                .scheduleRun(
                    appArn = appArn,
                    runConfiguration = runConfiguration,
                    devicePoolArn = devicePoolArn,
                    executionConfiguration = executionConfiguration,
                    runName = runName,
                    projectArn = projectArn,
                    testConfiguration = testExecutionConfiguration
                )

            //THEN
            response.shouldBeLeft() should {
                it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
                it shouldHaveMessage UNSUPPORTED_EXECUTION_TYPE.format(executionType.name)
            }
            verify {
                dfClient.scheduleRun(any<ScheduleRunRequest>()) wasNot called
            }
            confirmVerified(dfClient)
        }
    }

    "When a run name is provided it should be used"{
        checkAll(Exhaustive.collection(validTestTypes)) { executionType ->
            //GIVEN
            val testExecutionConfiguration = ScheduleRunTest.builder().type(executionType).build()
            val expectedRun = Run
                .builder()
                .arn(runARN)
                .build()
            every {
                dfClient
                    .scheduleRun(any<ScheduleRunRequest>())
            } returns ScheduleRunResponse.builder().run(expectedRun).build()

            //WHEN
            val response = DefaultDeviceFarmRunsHandler(dfClient)
                .scheduleRun(
                    appArn = appArn,
                    runConfiguration = runConfiguration,
                    devicePoolArn = devicePoolArn,
                    executionConfiguration = executionConfiguration,
                    runName = runName,
                    projectArn = projectArn,
                    testConfiguration = testExecutionConfiguration
                )

            //THEN
            response.shouldBeRight().shouldBe(expectedRun)
            verify {
                dfClient.scheduleRun(
                    ScheduleRunRequest
                        .builder()
                        .configuration(runConfiguration)
                        .devicePoolArn(devicePoolArn)
                        .executionConfiguration(executionConfiguration)
                        .projectArn(projectArn)
                        .name(runName)
                        .test(testExecutionConfiguration)
                        .apply {
                            if (executionType == TestType.APPIUM_NODE)
                                appArn(appArn)
                        }
                        .build()
                )
            }

            confirmVerified(dfClient)
        }
    }

    "When no app arn is provided it should return a DeviceFarmTractorErrorIllegalArgumentException as a left"{
        //WHEN
        val response = DefaultDeviceFarmRunsHandler(dfClient)
            .scheduleRun(
                appArn = "  ",
                runConfiguration = runConfiguration,
                devicePoolArn = devicePoolArn,
                executionConfiguration = executionConfiguration,
                runName = runName,
                projectArn = projectArn,
                testConfiguration = testNativeConfiguration
            )

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_APP_ARN
        }
        verify {
            dfClient.scheduleRun(any<ScheduleRunRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }

    "When no device pool arn is provided it should return a DeviceFarmTractorErrorIllegalArgumentException as a left"{
        checkAll(Exhaustive.collection(validTestTypes)) { executionType ->
            //GIVEN
            val testExecutionConfiguration = ScheduleRunTest.builder().type(executionType).build()
            //WHEN
            val response = DefaultDeviceFarmRunsHandler(dfClient)
                .scheduleRun(
                    appArn = appArn,
                    runConfiguration = runConfiguration,
                    devicePoolArn = "  ",
                    executionConfiguration = executionConfiguration,
                    runName = runName,
                    projectArn = projectArn,
                    testConfiguration = testExecutionConfiguration
                )

            //THEN
            response.shouldBeLeft() should {
                it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
                it shouldHaveMessage EMPTY_DEVICE_POOL_ARN
            }
            verify {
                dfClient.scheduleRun(any<ScheduleRunRequest>()) wasNot called
            }
            confirmVerified(dfClient)
        }
    }

    "When no project arn is provided it should return a DeviceFarmTractorErrorIllegalArgumentException as a left"{
        checkAll(Exhaustive.collection(validTestTypes)) { executionType ->
            //GIVEN
            val testExecutionConfiguration = ScheduleRunTest.builder().type(executionType).build()

            //WHEN
            val response = DefaultDeviceFarmRunsHandler(dfClient)
                .scheduleRun(
                    appArn = appArn,
                    runConfiguration = runConfiguration,
                    devicePoolArn = devicePoolArn,
                    executionConfiguration = executionConfiguration,
                    runName = runName,
                    projectArn = "  ",
                    testConfiguration = testExecutionConfiguration
                )

            //THEN
            response.shouldBeLeft() should {
                it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
                it shouldHaveMessage EMPTY_PROJECT_ARN
            }
            verify {
                dfClient.scheduleRun(any<ScheduleRunRequest>()) wasNot called
            }
            confirmVerified(dfClient)
        }
    }

    "When an error happens scheduling the run, it should return an ErrorSchedulingRun as a left"{
        checkAll(Exhaustive.collection(validTestTypes)) { executionType ->
            //GIVEN
            val testExecutionConfiguration = ScheduleRunTest.builder().type(executionType).build()
            val expectedError = RuntimeException("test error")
            every {
                dfClient
                    .scheduleRun(any<ScheduleRunRequest>())
            } throws expectedError

            //WHEN
            val response = DefaultDeviceFarmRunsHandler(dfClient)
                .scheduleRun(
                    appArn = appArn,
                    runConfiguration = runConfiguration,
                    devicePoolArn = devicePoolArn,
                    executionConfiguration = executionConfiguration,
                    projectArn = projectArn,
                    testConfiguration = testExecutionConfiguration
                )

            //THEN
            response.shouldBeLeft() should {
                it.shouldBeInstanceOf<ErrorSchedulingRun>()
                it shouldHaveMessage ERROR_SCHEDULING_RUN
                it.cause shouldBe expectedError
            }
            verify {
                dfClient.scheduleRun(any<ScheduleRunRequest>())
            }

            confirmVerified(dfClient)
        }
    }

    "When fetching a RUN it should return it as a right" {
        //GIVEN
        val expectedRun = Run
            .builder()
            .arn(runARN)
            .name(runName)
            .build()
        every { dfClient.getRun(any<GetRunRequest>()) } returns GetRunResponse.builder().run(expectedRun).build()

        //WHEN
        val response = DefaultDeviceFarmRunsHandler(dfClient).fetchRun(runARN)

        //THEN
        response.shouldBeRight() shouldBe expectedRun
        verify {
            dfClient.getRun(GetRunRequest.builder().arn(runARN).build())
        }
        confirmVerified(dfClient)
    }

    "When no run ARN is provided it should return a DeviceFarmTractorErrorIllegalArgumentException as a left"{
        //WHEN
        val response = DefaultDeviceFarmRunsHandler(dfClient).fetchRun(" ")

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
            it shouldHaveMessage EMPTY_RUN_ARN
        }
        verify {
            dfClient.getRun(any<GetRunRequest>()) wasNot called
        }
        confirmVerified(dfClient)
    }

    "When an error happens fetching a run it should return a ErrorFetchingAWSRun as a left"{
        //GIVEN
        val expectedError = RuntimeException("test error")
        every { dfClient.getRun(any<GetRunRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmRunsHandler(dfClient).fetchRun(runARN)

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorFetchingAWSRun>()
            it shouldHaveMessage ERROR_FETCHING_RUN.format(runARN)
            it.cause shouldBe expectedError
        }
        verify {
            dfClient.getRun(GetRunRequest.builder().arn(runARN).build())
        }
        confirmVerified(dfClient)
    }

    "When fetching all the jobs associated to a Run it should return them as a right"{
        //GIVEN
        val run = Run
            .builder()
            .arn(runARN)
            .name(runName)
            .build()
        val expectedJobs = (1..10)
            .map {
                Job
                    .builder()
                    .arn("arn:test:job:$it")
                    .build()
            }
        every {
            dfClient.listJobsPaginator(any<ListJobsRequest>()).jobs()
        } returns SdkIterable { expectedJobs.toMutableList().iterator() }

        //WHEN
        val response = DefaultDeviceFarmRunsHandler(dfClient)
            .getAssociatedJobs(run)

        //THEN
        response.shouldBeRight() shouldBe expectedJobs

        verify {
            dfClient.listJobsPaginator(ListJobsRequest.builder().arn(run.arn()).build())
        }
        confirmVerified(dfClient)
    }

    "When an error happens fetching the associated jobs of a Run then it should return a ErrorListingAssociatedJobs as a left"{
        //GIVEN
        val run = Run
            .builder()
            .arn(runARN)
            .name(runName)
            .build()
        val expectedError = RuntimeException("test error")
        every { dfClient.listJobsPaginator(any<ListJobsRequest>()) } throws expectedError

        //WHEN
        val response = DefaultDeviceFarmRunsHandler(dfClient)
            .getAssociatedJobs(run)

        //THEN
        response.shouldBeLeft() should {
            it.shouldBeInstanceOf<ErrorListingAssociatedJobs>()
            it shouldHaveMessage ERROR_FETCHING_JOBS.format(run.arn())
            it.cause shouldBe expectedError
        }
    }

    afterTest {
        clearMocks(dfClient)
    }
})