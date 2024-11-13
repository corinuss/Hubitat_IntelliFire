/**
 *  IntelliFire Fireplace Virtual Fan
 *
 *  Hubitat version created by Eric Will (corinuss)
 *
 *  This is an OPTIONAL virtual child device to help better interface with other
 *  Hubitat apps (such as those that expect to control the Fan with Level).
 *  Full Fan control can also be accomplished with the main Fireplace without this
 *  device.
 *
 *  MIT License
 *  Copyright (c) 2024 Eric Will
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 *  Change Log:
 *    11/12/2024 v2.3.0   - Created Fan virtual device.
 */

import groovy.transform.Field

metadata
{
    definition (name: "IntelliFire Fireplace Virtual Fan", namespace: "IntelliFire", author: "corinuss")
    {
        capability "Actuator"
        capability "FanControl"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"

        command 'setLevel', [[name: "Fan level percentage (0-100)*", type:"NUMBER", description:"Percentage is mapped to discrete Fan level values [0-4].  Used by SwitchLevel capability."]]
        command 'setSpeed', [[name: "Fan speed", type:"ENUM", constraints: FanControlSpeed]]

        attribute "light", "number"
    }
    
    preferences
    {
        input name: "enableDebugLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: false
    }
}

//================
// INITIALIZATION
//================
void configure()
{
    sendEvent(name: "supportedFanSpeeds", value: FanControlSpeed)
}

void logDebug (msg)
{
    if (enableDebugLogging)
    {
        log.debug msg
    }
}

void updateDeviceName(parentName)
{
    device.setName("$parentName Fan")
}

// Refresh
void refresh()
{
    // Refreshing the parent will cause it to notify us of any changes in setLightLevelFromParent.
    getParent().refresh()
}

// Switch
void on()
{
    setSpeed("on")
}

// Switch
void off()
{
    setSpeed("off")
}

// SwitchLevel
void setLevel(level, duration = 0)
{
    // 'duration' is not used
    getParent().setSpeedPercentage(level);
}

void setSpeed(String fanspeed)
{
    getParent().setSpeed(fanspeed)
}

void cycleSpeed()
{
    getParent().cycleSpeed()
}

// Should only be called from the Intellifire parent device.
void setFanSpeedFromParent(String fanspeed, level)
{
    sendEvent(name: "speed", value: fanspeed, descriptionText: "${device.getDisplayName()} level was set to $fanspeed")
    sendEvent(name: "level", value: level, unit: "%", descriptionText: "${device.getDisplayName()} level was set to $level%")

    def switchStatus = (level != 0) ? "on" : "off"
    sendEvent(name: "switch", value: switchStatus, descriptionText: "${device.getDisplayName()} was switched $switchStatus")
}

// Subset of officially supported FanControl speed names.
// TODO - Move this into a library to be shared
@Field FanControlSpeed =
[
    "off",
    "low",
    "medium",
    "medium-high",
    "high",
    "on"
]
