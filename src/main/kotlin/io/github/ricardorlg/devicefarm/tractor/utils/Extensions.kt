package io.github.ricardorlg.devicefarm.tractor.utils

import arrow.core.Either
import arrow.core.left
import software.amazon.awssdk.services.devicefarm.model.ArtifactType

fun ArtifactType.prettyName() = when (this) {
    ArtifactType.VIDEO -> "Recorded video"
    ArtifactType.CUSTOMER_ARTIFACT -> "Test reports"
    else -> name
}

inline fun <A, B> Either<A, B>.ensure(
    error: (B) -> A,
    predicate: (B) -> Boolean,
    predicateAction: () -> Unit
): Either<A, B> =
    when (this) {
        is Either.Right -> if (predicate(this.value)) {
            predicateAction()
            this
        } else {
            error(this.value).left()
        }
        is Either.Left -> this
    }