package com.provacor.sathi.core

import com.provacor.sathi.core.model.AgentIntent.DeviceControl
import com.provacor.sathi.core.model.AgentIntent.OpenApp
import com.provacor.sathi.core.model.AgentIntent.Search
import com.provacor.sathi.core.model.DeviceAction
import com.provacor.sathi.core.model.DeviceTarget
import com.provacor.sathi.core.parse.CommandInterpreter
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCommandTest {
    private val ci = CommandInterpreter()
    private fun parse(s: String) = ci.interpret(s)

    @Test fun flashlight() {
        assertEquals(listOf(DeviceControl(DeviceTarget.FLASHLIGHT, DeviceAction.ON)), parse("টর্চ জ্বালাও"))
        assertEquals(listOf(DeviceControl(DeviceTarget.FLASHLIGHT, DeviceAction.OFF)), parse("টর্চটা বন্ধ করো"))
        assertEquals(listOf(DeviceControl(DeviceTarget.FLASHLIGHT, DeviceAction.ON)), parse("turn on the flashlight"))
    }

    @Test fun volume() {
        assertEquals(listOf(DeviceControl(DeviceTarget.VOLUME, DeviceAction.UP)), parse("ভলিউম বাড়াও"))
        assertEquals(listOf(DeviceControl(DeviceTarget.VOLUME, DeviceAction.DOWN)), parse("সাউন্ড একটু কমাও"))
        assertEquals(listOf(DeviceControl(DeviceTarget.VOLUME, DeviceAction.MUTE)), parse("mute the volume"))
    }

    @Test fun switchesAndroidKeepsForTheUser() {
        assertEquals(listOf(DeviceControl(DeviceTarget.WIFI, DeviceAction.ON)), parse("ওয়াইফাই অন করো"))
        assertEquals(listOf(DeviceControl(DeviceTarget.BLUETOOTH, DeviceAction.OFF)), parse("bluetooth off"))
        assertEquals(listOf(DeviceControl(DeviceTarget.MOBILE_DATA, DeviceAction.ON)), parse("ইন্টারনেট চালু করো"))
    }

    @Test fun notADeviceCommand() {
        assertEquals(listOf(Search("ঢাকার আবহাওয়া")), parse("ইন্টারনেটে ঢাকার আবহাওয়া সার্চ করো"))
        assertEquals(listOf(OpenApp("ইউটিউব")), parse("ইউটিউব চালু করো"))
    }
}
