package io.github.ricardorlg.devicefarm.tractor

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.htmlreporter.HtmlReporter

object ProjectConfig : AbstractProjectConfig() {
    override val parallelism = 1
    override fun listeners() = listOf(HtmlReporter())
}
