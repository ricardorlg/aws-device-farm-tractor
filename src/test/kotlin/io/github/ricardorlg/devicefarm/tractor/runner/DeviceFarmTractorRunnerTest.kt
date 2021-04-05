package io.github.ricardorlg.devicefarm.tractor.runner

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.ricardorlg.devicefarm.tractor.model.*
import io.github.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmController
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.system.captureStandardErr
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.delay
import software.amazon.awssdk.services.devicefarm.model.*
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.time.hours
import kotlin.time.seconds

class DeviceFarmTractorRunnerTest : StringSpec({

    val expectedProjectArn = "arn:test:project"
    val projectName = "Test project"
    val devicePoolArn = "arn:test:device_pool"
    val devicePoolName = "Test device pool"
    val appUploadArn = "arn:test:app:upload"
    val app = tempfile(prefix = "testApp", suffix = ".apk")
    val appPath = app.path
    val appUploadName = app.name
    val appUploadType = UploadType.ANDROID_APP
    val testsUploadArn = "arn:test:tests_project:upload"
    val testsUploadName = "test_project.zip"
    val testsPath = "testsPath"
    val testSpecUploadArn = "arn:test_spec_upload"
    val testSpecUploadName = "testSpec.yml"
    val testSpecPath = "testSpecPath"
    val runName = "test Run unit test"

    val mockedDate = LocalDateTime.now()
    mockkStatic(LocalDateTime::class)
    every { LocalDateTime.now() } returns mockedDate

    "When running a test in device farm it should return the result" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _poolName ->
                _poolName shouldBe devicePoolName
                Either.Right(devicePool)
            },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, _ -> Either.Right(expectedRun) }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm it should use the correct Project ARN in the process" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { actualProjectArn, _ ->
                actualProjectArn shouldBe expectedProjectArn
                Either.Right(devicePool)
            },
            uploadArtifactToDeviceFarmImpl = { actualProjectArn, _, uploadType ->
                synchronized(this) {
                    withClue("The project arn used when uploading artifact to Device farm should be $expectedProjectArn") { actualProjectArn shouldBe expectedProjectArn }
                    when (uploadType) {
                        UploadType.ANDROID_APP -> {
                            appUpload
                        }
                        UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                            testsUpload
                        }
                        UploadType.APPIUM_NODE_TEST_SPEC -> {
                            testSpecUpload
                        }
                        else -> fail("the upload type $uploadType should never been passed as a parameter")
                    }.right()
                }
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, actualProjectArn, _ ->
                actualProjectArn shouldBe expectedProjectArn
                Either.Right(expectedRun)
            },
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm by default the capture video configuration should be enabled"{
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, executionConfiguration, _, _, _ ->
                withClue("by default the vide capture property should be enabled") {
                    executionConfiguration.videoCapture().shouldBeTrue()
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm the capture video configuration should be able to be specified"{
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, executionConfiguration, _, _, _ ->
                withClue("the video capture configuration should be disabled") {
                    executionConfiguration.videoCapture().shouldBeFalse()
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false,
            captureVideo = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm it should have APPIUM NODE as test type" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, testConfiguration ->
                withClue("the test execution type should be Appium Node") {
                    testConfiguration.type() shouldBe TestType.APPIUM_NODE
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm it should use the test project uploaded and the test spec provided" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, testConfiguration ->
                withClue("the test project and the test specification file should be the ones created by the runner") {
                    testConfiguration.testPackageArn() shouldBe testsUploadArn
                    testConfiguration.testSpecArn() shouldBe testSpecUploadArn
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm it should use the correct paths for the different upload types" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, path, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        withClue("the path for the app should be the correct one") { path shouldBe appPath }
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        withClue("the path for the test project should be the correct one") { path shouldBe testsPath }
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        withClue("the path for the test spec file should be the correct one") { path shouldBe testSpecPath }
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, _ -> Either.Right(expectedRun) }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm if no run name is provided it should create the name" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val expectedName =
            "Test_Run_${mockedDate.year}_${mockedDate.monthValue}_${mockedDate.dayOfMonth}_${mockedDate.hour}_${mockedDate.minute}"

        var actualRunName = ""

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _runName, _, _ ->
                actualRunName = _runName
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        actualRunName shouldBe expectedName
        //endregion
    }

    "When running a test in device farm if the base test report directory path is empty it should not download the results" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, _ -> Either.Right(expectedRun) },
            downloadAllTestReportsOfTestRunImpl = { _, _ -> fail("the reports should not be downloaded if no base report directory was provided") }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm if the base test report directory is provided it should use it as a path" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val testBaseReportDirectory = "base_test_reports_directory"

        val expectedPath = Paths.get(testBaseReportDirectory)

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, _ -> Either.Right(expectedRun) },
            downloadAllTestReportsOfTestRunImpl = { _, actualPath ->
                withClue("the base test reports directory should be used as a Path") {
                    actualPath shouldBe expectedPath
                }
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            testReportsBaseDirectory = testBaseReportDirectory,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm it should try to delete the uploads of the execution by default" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, _ -> Either.Right(expectedRun) },
            deleteUploadsImpl = { uploads ->
                uploads shouldContainExactly listOf(appUpload, testsUpload, testSpecUpload)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm, it should throw an error when no app path is provided"{
        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            DeviceFarmTractorRunner(MockedDeviceFarmController()).runTests(
                projectName = projectName,
                devicePoolName = devicePoolName,
                appPath = "   ",
                testProjectPath = testsPath,
                testSpecPath = testSpecPath,
                runName = runName,
                downloadReports = false,
                cleanStateAfterRun = false
            )
        }
        //endregion

        //region THEN
        error.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
        error shouldHaveMessage MANDATORY_PATH_PARAMETER
        //endregion
    }

    "When running a test in device farm, it should throw an error if a left is returned when loading the app upload type"{
        //region GIVEN
        val invalidApp = tempfile("testApp", ".zip")
        //endregion

        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            DeviceFarmTractorRunner(MockedDeviceFarmController()).runTests(
                projectName = projectName,
                devicePoolName = devicePoolName,
                appPath = invalidApp.path,
                testProjectPath = testsPath,
                testSpecPath = testSpecPath,
                runName = runName,
                downloadReports = false,
                cleanStateAfterRun = false
            )
        }
        //endregion

        //region THEN
        error.shouldBeInstanceOf<DeviceFarmTractorErrorIllegalArgumentException>()
        error shouldHaveMessage UNSUPPORTED_APP_FILE_EXTENSION.format(invalidApp.extension)
        //endregion
    }

    "When running a test in device farm it should throw an error if a left is returner by the projects handler" {
        //region GIVEN
        val expectedErrorMessage = "A test error message"
        val expectedErrorCause = RuntimeException(expectedErrorMessage)
        val expectedError = DeviceFarmTractorGeneralError(expectedErrorCause)
        val controller = MockedDeviceFarmController(findOrCreateProjectImpl = { Either.Left(expectedError) })
        //endregion

        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            val loggedErrorMessage = captureStandardErr {
                DeviceFarmTractorRunner(controller).runTests(
                    projectName = projectName,
                    devicePoolName = devicePoolName,
                    appPath = appPath,
                    testProjectPath = testsPath,
                    testSpecPath = testSpecPath,
                    runName = runName,
                    downloadReports = false,
                    cleanStateAfterRun = false
                )
            }
            loggedErrorMessage shouldBe expectedErrorMessage
        }
        //endregion

        //region THEN
        error shouldBe expectedError
        error shouldHaveMessage expectedErrorMessage
        error.cause shouldBe expectedErrorCause
        //endregion
    }

    "When running a test in device farm it should throw an error if a left is returner by the devices pool handler" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val expectedErrorMessage = "A test error message"
        val expectedErrorCause = RuntimeException(expectedErrorMessage)
        val expectedError = DeviceFarmTractorGeneralError(expectedErrorCause)

        val controller =
            MockedDeviceFarmController(
                findOrCreateProjectImpl = { Either.Right(project) },
                findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Left(expectedError) })
        //endregion

        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            val loggedErrorMessage = captureStandardErr {
                DeviceFarmTractorRunner(controller).runTests(
                    projectName = projectName,
                    devicePoolName = devicePoolName,
                    appPath = appPath,
                    testProjectPath = testsPath,
                    testSpecPath = testSpecPath,
                    runName = runName,
                    downloadReports = false,
                    cleanStateAfterRun = false
                )
            }
            loggedErrorMessage shouldBe expectedErrorMessage
        }
        //endregion

        //region THEN
        error shouldBe expectedError
        error shouldHaveMessage expectedErrorMessage
        error.cause shouldBe expectedErrorCause
        //endregion
    }

    "When running a test in device farm it should throw an error if a left is returner by the upload artifacts handler" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val expectedErrorMessage = "A test error message"
        val expectedErrorCause = RuntimeException(expectedErrorMessage)
        val expectedError = DeviceFarmTractorGeneralError(expectedErrorCause)

        val controller =
            MockedDeviceFarmController(
                findOrCreateProjectImpl = { Either.Right(project) },
                findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
                uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                    when (uploadType) {
                        UploadType.ANDROID_APP -> {
                            appUpload.right()
                        }
                        UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                            testsUpload.right()
                        }
                        UploadType.APPIUM_NODE_TEST_SPEC -> {
                            expectedError.left()
                        }
                        else -> fail("the upload type $uploadType should never been passed as a parameter")
                    }
                }
            )
        //endregion

        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            val loggedErrorMessage = captureStandardErr {
                DeviceFarmTractorRunner(controller).runTests(
                    projectName = projectName,
                    devicePoolName = devicePoolName,
                    appPath = appPath,
                    testProjectPath = testsPath,
                    testSpecPath = testSpecPath,
                    runName = runName,
                    downloadReports = false,
                    cleanStateAfterRun = false
                )
            }
            loggedErrorMessage shouldBe expectedErrorMessage
        }
        //endregion

        //region THEN
        error shouldBe expectedError
        error shouldHaveMessage expectedErrorMessage
        error.cause shouldBe expectedErrorCause
        //endregion
    }

    "When running a test in device farm it should throw an error if a left is returner by the upload artifacts handler and the parallel upload process should be cancelled".config(
        timeout = 1.seconds
    ) {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val expectedErrorMessage = "A test error message"
        val expectedErrorCause = RuntimeException(expectedErrorMessage)
        val expectedError = DeviceFarmTractorGeneralError(expectedErrorCause)

        val controller =
            MockedDeviceFarmController(
                findOrCreateProjectImpl = { Either.Right(project) },
                findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
                uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                    when (uploadType) {
                        UploadType.ANDROID_APP -> {
                            delay(10.hours)
                            appUpload.right()
                        }
                        UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                            testsUpload.right()
                        }
                        UploadType.APPIUM_NODE_TEST_SPEC -> {
                            expectedError.left()
                        }
                        else -> fail("the upload type $uploadType should never been passed as a parameter")
                    }
                }
            )
        //endregion

        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            val loggedErrorMessage = captureStandardErr {
                DeviceFarmTractorRunner(controller).runTests(
                    projectName = projectName,
                    devicePoolName = devicePoolName,
                    appPath = appPath,
                    testProjectPath = testsPath,
                    testSpecPath = testSpecPath,
                    runName = runName,
                    downloadReports = false,
                    cleanStateAfterRun = false
                )
            }
            loggedErrorMessage shouldBe expectedErrorMessage
        }
        //endregion

        //region THEN
        error shouldBe expectedError
        error shouldHaveMessage expectedErrorMessage
        error.cause shouldBe expectedErrorCause
        //endregion
    }

    "When running a test in device farm it should throw an error if a left is returner by the run handler" {
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedErrorMessage = "A test error message"
        val expectedErrorCause = RuntimeException(expectedErrorMessage)
        val expectedError = DeviceFarmTractorGeneralError(expectedErrorCause)

        val controller =
            MockedDeviceFarmController(
                findOrCreateProjectImpl = { Either.Right(project) },
                findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
                uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                    when (uploadType) {
                        UploadType.ANDROID_APP -> {
                            appUpload
                        }
                        UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                            testsUpload
                        }
                        UploadType.APPIUM_NODE_TEST_SPEC -> {
                            testSpecUpload
                        }
                        else -> fail("the upload type $uploadType should never been passed as a parameter")
                    }.right()
                },
                scheduleRunAndWaitImpl = { _, _, _, _, _, _, _ -> Either.Left(expectedError) }
            )
        //endregion

        //region WHEN
        val error = shouldThrow<DeviceFarmTractorError> {
            val loggedErrorMessage = captureStandardErr {
                DeviceFarmTractorRunner(controller).runTests(
                    projectName = projectName,
                    devicePoolName = devicePoolName,
                    appPath = appPath,
                    testProjectPath = testsPath,
                    testSpecPath = testSpecPath,
                    runName = runName,
                    downloadReports = false,
                    cleanStateAfterRun = false
                )
            }
            loggedErrorMessage shouldBe expectedErrorMessage
        }
        //endregion

        //region THEN
        error shouldBe expectedError
        error shouldHaveMessage expectedErrorMessage
        error.cause shouldBe expectedErrorCause
        //endregion
    }

    "When running a test in device farm, it should be a metered execution by default"{
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, scheduleRunConfiguration, _, _, _, _, _ ->
                withClue("by default the test execution should be metered") {
                    scheduleRunConfiguration.billingMethod() shouldBe BillingMethod.METERED
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm, it should allow unmetered executions"{
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, scheduleRunConfiguration, _, _, _, _, _ ->
                withClue("unmetered executions should be available") {
                    scheduleRunConfiguration.billingMethod() shouldBe BillingMethod.UNMETERED
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false,
            meteredTests = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm, it should have the app performance monitoring enable by default"{
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, scheduleRunTestConfiguration ->
                withClue("the test should have the app performance monitoring enable by default") {
                    scheduleRunTestConfiguration.parameters()
                        .getOrDefault(APP_PERFORMANCE_MONITORING_PARAMETER_KEY, "true").toBoolean().shouldBeTrue()
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    "When running a test in device farm, it should allow to disable the app performance monitoring"{
        //region GIVEN
        val project = Project
            .builder()
            .arn(expectedProjectArn)
            .name(projectName)
            .build()

        val devicePool = DevicePool
            .builder()
            .arn(devicePoolArn)
            .name(devicePoolName)
            .build()

        val appUpload = Upload
            .builder()
            .arn(appUploadArn)
            .name(appUploadName)
            .type(appUploadType)
            .build()

        val testsUpload = Upload
            .builder()
            .arn(testsUploadArn)
            .name(testsUploadName)
            .type(UploadType.APPIUM_NODE_TEST_PACKAGE)
            .build()

        val testSpecUpload = Upload
            .builder()
            .arn(testSpecUploadArn)
            .name(testSpecUploadName)
            .type(UploadType.APPIUM_NODE_TEST_SPEC)
            .build()

        val expectedRun = Run
            .builder()
            .name(runName)
            .status(ExecutionStatus.COMPLETED)
            .result(ExecutionResult.PASSED)
            .build()

        val controller = MockedDeviceFarmController(
            findOrCreateProjectImpl = { Either.Right(project) },
            findOrUseDefaultDevicePoolImpl = { _, _ -> Either.Right(devicePool) },
            uploadArtifactToDeviceFarmImpl = { _, _, uploadType ->
                when (uploadType) {
                    UploadType.ANDROID_APP -> {
                        appUpload
                    }
                    UploadType.APPIUM_NODE_TEST_PACKAGE -> {
                        testsUpload
                    }
                    UploadType.APPIUM_NODE_TEST_SPEC -> {
                        testSpecUpload
                    }
                    else -> fail("the upload type $uploadType should never been passed as a parameter")
                }.right()
            },
            scheduleRunAndWaitImpl = { _, _, _, _, _, _, scheduleRunTestConfiguration ->
                withClue("the test should have the app performance monitoring disabled") {
                    scheduleRunTestConfiguration.parameters()
                        .getOrDefault(APP_PERFORMANCE_MONITORING_PARAMETER_KEY, "true").toBoolean().shouldBeFalse()
                }
                Either.Right(expectedRun)
            }
        )
        //endregion

        //region WHEN
        val response = DeviceFarmTractorRunner(controller).runTests(
            projectName = projectName,
            devicePoolName = devicePoolName,
            appPath = appPath,
            testProjectPath = testsPath,
            testSpecPath = testSpecPath,
            runName = runName,
            downloadReports = false,
            cleanStateAfterRun = false,
            disablePerformanceMonitoring = true
        )
        //endregion

        //region THEN
        response shouldBe expectedRun
        //endregion
    }

    afterTest {
        unmockkStatic(LocalDateTime::class)
    }
})
