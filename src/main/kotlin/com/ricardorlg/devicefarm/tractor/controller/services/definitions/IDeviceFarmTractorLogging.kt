package com.ricardorlg.devicefarm.tractor.controller.services.definitions

import arrow.core.Either
import java.util.logging.Level

interface IDeviceFarmTractorLogging {
    fun logStatus(msg: String)
    fun logError(error:Throwable?=null,msg:String)
}
