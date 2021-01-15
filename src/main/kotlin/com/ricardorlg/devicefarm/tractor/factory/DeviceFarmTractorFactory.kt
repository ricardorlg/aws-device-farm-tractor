package com.ricardorlg.devicefarm.tractor.factory

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.DefaultDeviceFarmTractorController
import com.ricardorlg.devicefarm.tractor.controller.services.definitions.IDeviceFarmTractorLogging
import com.ricardorlg.devicefarm.tractor.controller.services.implementations.*
import com.ricardorlg.devicefarm.tractor.runner.DeviceFarmTractorRunner
import org.http4k.client.JavaHttpClient
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.devicefarm.DeviceFarmClientBuilder


object DeviceFarmTractorFactory {

    suspend fun createRunner(
        deviceFarmClientBuilder: DeviceFarmClientBuilder,
        logger: IDeviceFarmTractorLogging,
        accessKeyId: String = "",
        secretAccessKey: String = "",
        sessionToken: String = "",
        region: String = ""
    ): Either<Throwable, DeviceFarmTractorRunner> {
        return Either.catch {

            deviceFarmClientBuilder
                .applyMutation {
                    it.credentialsProvider(
                        awsCredentialsProviderFromRunConfiguration(
                            logger,
                            accessKeyId,
                            secretAccessKey,
                            sessionToken
                        )
                    )
                    if (region.isNotBlank()) {
                        logger.logStatus("I will create the device farm client using the provided region")
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

            DeviceFarmTractorRunner(controller = controller, logger = logger)
        }
    }

    private fun awsCredentialsProviderFromRunConfiguration(
        logger: IDeviceFarmTractorLogging,
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: String
    ): AwsCredentialsProvider {

        return when {
            accessKeyId.isBlank() || secretAccessKey.isBlank() -> {
                DefaultCredentialsProvider.create()
            }
            sessionToken.isBlank() -> {
                logger.logStatus("I will create the device farm client using the provided credentials")
                StaticCredentialsProvider
                    .create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
            }
            else -> {
                logger.logStatus("I will create the device farm client using the provided credentials and session token")
                StaticCredentialsProvider
                    .create(AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken))
            }
        }

    }
}