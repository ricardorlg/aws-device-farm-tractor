package io.github.ricardorlg.devicefarm.tractor.factory

import io.github.ricardorlg.devicefarm.tractor.stubs.MockedDeviceFarmLogging
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.DeviceFarmClientBuilder

class DeviceFarmTractorFactoryTest : StringSpec({

    val logger = MockedDeviceFarmLogging()
    val profileName = "test_profile"
    val expectedDefaultCredentialsProvider = DefaultCredentialsProvider.create()
    val accessKeyId = "test_access_key"
    val secretAccessKey = "test_secret_access_key"
    val sessionToken = "test_session_token"
    val expectedBasicCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
    val expectedSessionCredentials = AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)

    val dfClient = mockk<DeviceFarmClient>()
    val builder = spyk<DeviceFarmClientBuilder>()

    beforeTest {
        every { builder.build() } returns dfClient
    }

    "When creating a runner without credentials and region, it should return a runner that uses a device farm client with default configuration" {
        //GIVEN
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(deviceFarmClientBuilder = builder, logger = logger)

        //THEN
        actualRunner.shouldBeRight()
        credentialsProviderSlot.captured shouldBe expectedDefaultCredentialsProvider

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<StaticCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(expectedDefaultCredentialsProvider)
            builder.hint(DeviceFarmClient::class).build()
        }

    }

    "When creating a runner without credentials and region but with a given profile name, it should return a runner that uses a device farm client with default configuration using given profile" {
        //GIVEN
        mockkStatic(ProfileCredentialsProvider::class)
        val mock = mockk<ProfileCredentialsProvider>()
        every { ProfileCredentialsProvider.create(profileName) } returns mock
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(
            deviceFarmClientBuilder = builder,
            logger = logger,
            profileName = profileName
        )

        //THEN
        actualRunner.shouldBeRight()
        credentialsProviderSlot.captured shouldBe mock

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<DefaultCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class)
                .credentialsProvider(capture(slot<StaticCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(mock)
            builder.hint(DeviceFarmClient::class).build()
        }

        unmockkStatic(ProfileCredentialsProvider::class)

    }


    "When only accessKeyId is provided, it should return a runner that uses a device farm client with default configuration" {
        //GIVEN
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(
            deviceFarmClientBuilder = builder,
            logger = logger,
            accessKeyId = "accessKeyId"
        )

        //THEN
        actualRunner.shouldBeRight()
        credentialsProviderSlot.captured shouldBe expectedDefaultCredentialsProvider

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<StaticCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(expectedDefaultCredentialsProvider)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When only secretAccessKey is provided, it should return a runner that uses a device farm client with default configuration" {
        //GIVEN
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(
            deviceFarmClientBuilder = builder,
            logger = logger,
            secretAccessKey = "secretAccessKey"
        )

        //THEN
        actualRunner.shouldBeRight()
        credentialsProviderSlot.captured shouldBe expectedDefaultCredentialsProvider

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<StaticCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(expectedDefaultCredentialsProvider)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When sessionToken is provided but no credentials, it should return a runner that uses a device farm client with default configuration" {
        //GIVEN
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(
            deviceFarmClientBuilder = builder,
            logger = logger,
            secretAccessKey = "secretAccessKey"
        )

        //THEN
        actualRunner.shouldBeRight()
        credentialsProviderSlot.captured shouldBe expectedDefaultCredentialsProvider

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<StaticCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(expectedDefaultCredentialsProvider)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When accessKeyId and secretAccessKey are provided, it should return a runner that uses a device farm client with basic credentials" {
        //GIVEN
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner =
            DeviceFarmTractorFactory.createRunner(
                deviceFarmClientBuilder = builder,
                logger = logger,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey
            )

        val actualCredentialsProvider = credentialsProviderSlot.captured

        //THEN
        actualRunner.shouldBeRight()
        actualCredentialsProvider.shouldBeInstanceOf<StaticCredentialsProvider>()
        actualCredentialsProvider.resolveCredentials() shouldBe expectedBasicCredentials

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<DefaultCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(actualCredentialsProvider)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When basic credentials and sessionToken are provided, it should return a runner that uses a device farm client with the basic credentials and session token" {
        //GIVEN
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder

        //WHEN
        val actualRunner =
            DeviceFarmTractorFactory.createRunner(
                deviceFarmClientBuilder = builder,
                logger = logger,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                sessionToken = sessionToken
            )

        val actualCredentialsProvider = credentialsProviderSlot.captured

        //THEN
        actualRunner.shouldBeRight()
        actualCredentialsProvider.shouldBeInstanceOf<StaticCredentialsProvider>()
        actualCredentialsProvider.resolveCredentials() shouldBe expectedSessionCredentials

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<DefaultCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(actualCredentialsProvider)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When creating a runner without credentials but with region, it should return a runner that uses a device farm client with default configuration but that use the provided region" {
        //GIVEN
        val testRegion = Region.regions().random()
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder
        every { builder.region(any()) } returns builder

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(
            deviceFarmClientBuilder = builder,
            logger = logger,
            region = testRegion.id()
        )

        //THEN
        actualRunner.shouldBeRight()
        credentialsProviderSlot.captured shouldBe expectedDefaultCredentialsProvider

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<StaticCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(expectedDefaultCredentialsProvider)
            builder.hint(DeviceFarmClientBuilder::class).region(testRegion)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When accessKeyId, secretAccessKey and region name are provided, it should return a runner that uses a device farm client with basic credentials and the provided region" {
        //GIVEN
        val testRegion = Region.regions().random()
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder
        every { builder.region(any()) } returns builder

        //WHEN
        val actualRunner =
            DeviceFarmTractorFactory.createRunner(
                deviceFarmClientBuilder = builder,
                logger = logger,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                region = testRegion.id()
            )

        val actualCredentialsProvider = credentialsProviderSlot.captured

        //THEN
        actualRunner.shouldBeRight()
        actualCredentialsProvider.shouldBeInstanceOf<StaticCredentialsProvider>()
        actualCredentialsProvider.resolveCredentials() shouldBe expectedBasicCredentials

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<DefaultCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(actualCredentialsProvider)
            builder.hint(DeviceFarmClientBuilder::class).region(testRegion)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When accessKeyId, secretAccessKey, session token and region name are provided, it should return a runner that uses a device farm client with basic credentials, the session token and the provided region" {
        //GIVEN
        val testRegion = Region.regions().random()
        val credentialsProviderSlot = slot<AwsCredentialsProvider>()
        every { builder.credentialsProvider(capture(credentialsProviderSlot)) } returns builder
        every { builder.region(any()) } returns builder

        //WHEN
        val actualRunner =
            DeviceFarmTractorFactory.createRunner(
                deviceFarmClientBuilder = builder,
                logger = logger,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                sessionToken = sessionToken,
                region = testRegion.id()
            )

        val actualCredentialsProvider = credentialsProviderSlot.captured

        //THEN
        actualRunner.shouldBeRight()
        actualCredentialsProvider.shouldBeInstanceOf<StaticCredentialsProvider>()
        actualCredentialsProvider.resolveCredentials() shouldBe expectedSessionCredentials

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(capture(slot<DefaultCredentialsProvider>())) wasNot called
            builder.hint(DeviceFarmClientBuilder::class).credentialsProvider(actualCredentialsProvider)
            builder.hint(DeviceFarmClientBuilder::class).region(testRegion)
            builder.hint(DeviceFarmClient::class).build()
        }
    }

    "When an error happens, it should return the exception wrapped as a left"{
        //GIVEN
        val expectedError = RuntimeException("test error")
        every { builder.credentialsProvider(any()) } throws expectedError

        //WHEN
        val actualRunner = DeviceFarmTractorFactory.createRunner(deviceFarmClientBuilder = builder, logger = logger)

        //THEN
        actualRunner.shouldBeLeft() shouldBe expectedError

        verifySequence {
            builder.applyMutation(any())
            builder.credentialsProvider(any())
            builder.build() wasNot called
        }
    }

    afterTest {
        clearMocks(builder)
    }
})
