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
 *  Copyright (c) 2023 Eric Will
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
 *    11/15/2023 v1.1.1   - Fixed the description text in events.
 *    11/12/2023 v1.1.0   - Initial version of Light virtual device.
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

    sendEvent(name: "light", value: level, descriptionText: "Light level")
    sendEvent(name: "level", value: levelPercentage, unit: "%", descriptionText: "Light level percentage")
    sendEvent(name: "switch", value: level != 0, descriptionText: "Light is on")
}
