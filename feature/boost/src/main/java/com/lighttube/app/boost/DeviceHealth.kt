package com.lighttube.app.boost

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

data class DeviceHealth(val available: Long, val total: Long, val battery: Int?, val powerSaver: Boolean) {
    companion object {
        fun read(context: Context): DeviceHealth {
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return DeviceHealth(memory.availMem, memory.totalMem,
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 },
                power.isPowerSaveMode)
        }
    }
}
