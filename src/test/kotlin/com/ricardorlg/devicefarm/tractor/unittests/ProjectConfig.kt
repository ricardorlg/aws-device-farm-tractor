package com.ricardorlg.devicefarm.tractor.unittests

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.allure.AllureTestReporter

object ProjectConfig : AbstractProjectConfig() {
  override fun listeners() = listOf(AllureTestReporter())
  override val parallelism = 6
}
