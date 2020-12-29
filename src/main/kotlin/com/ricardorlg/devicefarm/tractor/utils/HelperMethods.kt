package com.ricardorlg.devicefarm.tractor.utils

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmIllegalArtifactExtension
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

    suspend fun loadFileFromPath(path: String): Either<Throwable, File> {
        return Either.catch {
            require(path.isNotEmpty()) { "The path parameter is mandatory" }
            val f = File(path)
            if (f.exists()) {
                f
            } else {
                throw RuntimeException("The file $path does not exists")
            }
        }
    }
}