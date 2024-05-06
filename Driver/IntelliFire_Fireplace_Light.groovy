/**
 *  IntelliFire Fireplace Virtual Light
 *
 *  Hubitat version created by Eric Will (corinuss)
 *
 *  This is a virtual device intended to extend the main Fireplace device to fully
 *  support the Light capability.  Switch and Light capabilities cannot currently
 *  co-exist on Hubitat to have different functionalities on the same device due
 *  to an overlapping interface.
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
 *    05/05/2024 v2.1.0   - Event descriptions updated to describe what happened.  (Hubitat standard.)
 *    01/15/2024 v2.0.0   - Cloud Control support and a lot of cleanup.  See Release Notes for details.
 */

metadata
{
    definition (name: "IntelliFire Fireplace Virtual Light", namespace: "IntelliFire", author: "corinuss")
    {
        capability "Light"
        capability "Refresh"
        capability "Switch"
		capability "Switch Level"

        command 'setLevel', [[name: "Light level percentage (0-100)*", type:"NUMBER", description:"Percentage is mapped to discrete Light level values [0-3].  Used by SwitchLevel capability."]]
        command 'setLightLevel', [[name: "Light level (0-3)*", type:"NUMBER"]]

        attribute "light", "number"
    }
    
    preferences
    {
        input name: "enableDebugLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: false
    }
}

void logDebug (msg)
{
    if (enableDebugLogging)
    {
        log.debug msg
    }
}

// Refresh
void refresh()
{
    // Refreshing the parent will cause it to notify us of any changes in setLightLevelFromParent.
    getParent().refresh()
}

// Light, Switch
void on()
{
    getParent().lightOn()
}

// Light, Switch
void off()
{
    getParent().lightOff()
}

// SwitchLevel
void setLevel(level, duration = 0)
{
    // 'duration' is not used
    def lightLevel = (int)((level+33)/33.3);
    setLightLevel(lightLevel);
}

void setLightLevel(level)
{
    getParent().setLightLevel(level)
}

// Should only be called from the Intellifire parent device.
void setLightLevelFromParent(level)
{
    levelPercentage = (int)(level * 33.34)

    sendEvent(name: "light", value: level, descriptionText: "${device.getDisplayName()} level was set to $level")
    sendEvent(name: "level", value: levelPercentage, unit: "%", descriptionText: "${device.getDisplayName()} level was set to $levelPercentage%")

    def switchStatus = (level != 0) ? "on" : "off"
    sendEvent(name: "switch", value: switchStatus, descriptionText: "${device.getDisplayName()} was switched $switchStatus")
}
