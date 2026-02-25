package com.jacksonrakena.mixer.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Logback MDC converter that:
 *  - Omits `jobrunr.jobName` and `jobrunr.jobSignature` (full class-name strings, too noisy)
 *  - Shortens `jobrunr.jobId` to its last 12 characters
 *  - Passes all other keys through unchanged
 *  - Renders nothing (empty string) when the resulting map is empty
 */
class ShortMdcConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String {
        val mdc = event.mdcPropertyMap ?: return ""

        val entries = mdc.entries
            .filter { (k, _) -> k != "jobrunr.jobName" && k != "jobrunr.jobSignature" }
            .map { (k, v) ->
                val displayValue = if (k == "jobrunr.jobId") v.takeLast(12) else v
                "$k=$displayValue"
            }

        return if (entries.isEmpty()) "" else entries.joinToString(", ")
    }
}
