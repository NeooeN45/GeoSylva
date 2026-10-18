package com.forestry.counter.data.service

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading

/** Boot count and elapsedRealtime remain stable across an application process restart. */
class AndroidMartelageClock(context: Context) : MartelageClock {
    private val resolver = context.applicationContext.contentResolver
    override fun read() = MartelageClockReading(
        wallTimeMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootCount = runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT) }
            .getOrNull()?.takeIf { it >= 0 },
    )
}
