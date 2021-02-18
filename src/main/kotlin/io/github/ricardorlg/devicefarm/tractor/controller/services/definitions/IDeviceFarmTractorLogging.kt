package io.github.ricardorlg.devicefarm.tractor.controller.services.definitions

interface IDeviceFarmTractorLogging {
    fun logStatus(msg: String)
    fun logError(error:Throwable?=null,msg:String)
}
