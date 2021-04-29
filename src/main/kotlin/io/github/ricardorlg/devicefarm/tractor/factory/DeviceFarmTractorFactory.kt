package io.github.ricardorlg.devicefarm.tractor.factory

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController
import io.github.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import io.github.ricardorlg.devicefarm.tractor.controller.services.implementations.*
import io.github.ricardorlg.devicefarm.tractor.runner.DeviceFarmTractorRunner
import org.http4k.client.JavaHttpClient
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.devicefarm.DeviceFarmClient
import software.amazon.awssdk.services.devicefarm.DeviceFarmClientBuilder


object DeviceFarmTractorFactory {

    fun createRunner(
        deviceFarmClientBuilder: DeviceFarmClientBuilder = DeviceFarmClient.builder(),
        logger: IDeviceFarmTractorLogging,
        accessKeyId: String = "",
        secretAccessKey: String = "",
        sessionToken: String = "",
        region: String = "",
        profileName: String = "",
    ): Either<Throwable, DeviceFarmTractorRunner> {
        return Either.catch {
            deviceFarmClientBuilder
                .applyMutation {
                    it.credentialsProvider(
                        awsCredentialsProviderFromRunConfiguration(
                            logger,
                            accessKeyId,
                            secretAccessKey,
                            sessionToken,
                            profileName
                        )
                    )
                    if (region.isNotBlank()) {
                        logger.logMessage("I will create the device farm client using the provided region")
                        it.region(Region.of(region))
                    }

                }

            val dfClient = deviceFarmClientBuilder.build()
            val controller = DefaultDeviceFarmTractorController(
                deviceFarmTractorLogging = logger,
                deviceFarmProjectsHandler = DefaultDeviceFarmProjectsHandler(dfClient),
                deviceFarmDevicePoolsHandler = DefaultDeviceFarmDevicePoolsHandler(dfClient),
                deviceFarmUploadArtifactsHandler = DefaultDeviceFarmUploadArtifactsHandler(dfClient, JavaHttpClient()),
                deviceFarmRunsHandler = DefaultDeviceFarmRunsHandler(dfClient),
                deviceFarmArtifactsHandler = DefaultDeviceFarmArtifactsHandler(dfClient)
            )

            DeviceFarmTractorRunner(controller = controller)
        }
    }

    private fun awsCredentialsProviderFromRunConfiguration(
        logger: IDeviceFarmTractorLogging,
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: String,
        profileName: String
    ): AwsCredentialsProvider {
        return when {
            accessKeyId.isBlank() || secretAccessKey.isBlank() -> {
                if (profileName.isNotBlank()) {
                    logger.logMessage("I will use the credential using $profileName as profile")
                    ProfileCredentialsProvider.create(profileName)
                } else {
                    logger.logMessage("I will use the default AWS credentials")
                    DefaultCredentialsProvider.create()
                }
            }
            sessionToken.isBlank() -> {
                logger.logMessage("I will create the device farm client using the provided credentials")
                StaticCredentialsProvider
                    .create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
            }
            else -> {
                logger.logMessage("I will create the device farm client using the provided credentials and session token")
                StaticCredentialsProvider
                    .create(AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken))
            }
        }

    }
}