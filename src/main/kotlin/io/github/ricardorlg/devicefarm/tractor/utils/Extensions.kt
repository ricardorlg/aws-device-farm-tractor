package io.github.ricardorlg.devicefarm.tractor.utils

import software.amazon.awssdk.services.devicefarm.model.ArtifactType

fun ArtifactType.prettyName() = when (this) {
    ArtifactType.VIDEO -> "Recorded video"
    ArtifactType.CUSTOMER_ARTIFACT -> "Test reports"
    else -> name
}