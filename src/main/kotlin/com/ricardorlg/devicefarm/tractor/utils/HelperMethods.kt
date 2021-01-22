package com.ricardorlg.devicefarm.tractor.utils

import arrow.core.Either
import arrow.core.left
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorErrorIllegalArgumentException
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmIllegalArtifactExtension
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorGeneralError
import software.amazon.awssdk.services.devicefarm.model.UploadType
import software.amazon.awssdk.services.devicefarm.model.UploadType.*
import java.io.File


object HelperMethods {

    fun File.validateFileExtensionByType(uploadType: UploadType) = when (uploadType) {
        ANDROID_APP -> {
            Either.conditionally(
                test = extension.endsWith("apk", true),
                ifFalse = {
                    DeviceFarmIllegalArtifactExtension
                        .InvalidAndroidExtension(nameWithoutExtension)
                },
                ifTrue = { this }
            )
        }
        IOS_APP -> {
            Either.conditionally(
                test = extension.endsWith("ipa", true),
                ifFalse = {
                    DeviceFarmIllegalArtifactExtension
                        .InvalidIOSExtension(nameWithoutExtension)
                },
                ifTrue = { this }
            )
        }
        APPIUM_NODE_TEST_SPEC -> {
            Either.conditionally(
                test = extension.endsWith("yml", true),
                ifFalse = {
                    DeviceFarmIllegalArtifactExtension
                        .InvalidSpecExtension(nameWithoutExtension)
                },
                ifTrue = { this }
            )
        }
        UNKNOWN_TO_SDK_VERSION -> Either.left(
            DeviceFarmIllegalArtifactExtension
                .UnsupportedException(uploadType)
        )
        else -> {
            Either.conditionally(
                test = extension.endsWith("zip", true),
                ifFalse = {
                    DeviceFarmIllegalArtifactExtension
                        .InvalidGeneralFileExtension(nameWithoutExtension)
                },
                ifTrue = { this }
            )
        }
    }

    suspend fun loadFileFromPath(path: String): Either<DeviceFarmTractorError, File> {
        return if (path.isBlank()) {
            DeviceFarmTractorErrorIllegalArgumentException("The path parameter is mandatory").left()
        } else {
            Either.catch {
                val f = File(path)
                if (f.exists()) {
                    f
                } else {
                    throw IllegalArgumentException("The file at $path does not exists")
                }
            }.mapLeft {
                DeviceFarmTractorGeneralError(it)
            }
        }
    }
}