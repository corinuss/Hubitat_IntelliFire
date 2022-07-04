/**
 *  IntelliFire Fireplace
 *
 *  Hubitat version created by Eric Will (corinuss)
 *
 *  Based heavily on 'intellifire4py' by jeeftor for Home Assistant.
 *  https://github.com/jeeftor/intellifire4py
 *
 *  MIT License
 *  Copyright (c) 2022 Eric Will
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
 *    07/03/2022 v0.3.0   - Automatic polling.
 *    07/03/2022 v0.2.0   - Added manual Polling.  Added a few missing commands.  On() can restore previous thermostat mode.  Code cleanup.
 *    06/22/2022 v0.1.0   - Initial publish.  Basic commands implemented but not stable.
 *
 */

import groovy.transform.Field
import hubitat.helper.HexUtils
import java.security.MessageDigest

metadata
{
    definition (name: 'IntelliFire Fireplace', namespace: 'IntelliFire', author: 'Eric Will')
    {
        capability "FanControl"
        //capability "Polling"    // Redundant.  "Refresh" seems more appropriate for this in the Hubitat world.
        capability "Refresh"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatSetpoint"
        capability "Tone"
        
        command 'configure'
        command 'setSpeed', [[name: "Fan speed", type:"ENUM", constraints: FanControlSpeed]]
        command 'setPilotLight', [[name: "Pilot light", type:"ENUM", description:"Enable the cold-weather pilot light?", constraints: OnOffValue.collect {k,v -> k}]]
        command 'setLightLevel', [[name: "Light level (0-3)*", type:"NUMBER"]]
        command 'setFlameHeight', [[name: "Flame height (0-4)*", type:"NUMBER"]]
        command 'setTimer', [[name: "Timer (0-180)*", description:"Minutes until the fireplace turns off.  0 to disable.", type:"NUMBER"]]
        command 'setThermostatMode', [[name: "Thermostat Mode", description:"Allow thermostat to control flame?", type:"ENUM", constraints: OnOffValue.collect {k,v -> k}]]

        attribute "fanspeed", "number"
        attribute "height", "number"
        attribute "light", "number"
        attribute "pilot", "number"
        attribute "power", "number"
        attribute "setpoint", "number"
        attribute "setpointLast", "number"
        attribute "temperatureRaw", "number"
        attribute "thermostat", "number"
        attribute "timer", "number"
        attribute "timeremaining", "number"
    }
    
    preferences
    {
        input name: "ipAddress", type: "text", title: "Local IP Address", required: true
        input name: "apiKey", type: "text", title: "API Key", description: "Find this on IntelliFire's servers", required: true
        input name: "userId", type: "text", title: "User ID", description: "Find this on IntelliFire's servers", required: true
        input name: "thermostatOnDefault", type: "bool", title: "Thermostat on by default?", description: "When turning on the fireplace, should the thermostat be enabled by default?", required: true, defaultValue: false
        input name: "enableDebugLogging", type: "bool", title: "Enable Debug Logging?", required: true, defaultValue: false
    }
}

void logDebug (msg)
{
	if (enableDebugLogging)
    {
		log.debug msg
	}
}

void installed()
{
    configure()
}

void configure()
{
    sendEvent(name: "supportedFanSpeeds", value: FanControlSpeed)
    refresh(true)
}

void poll()
{
    refresh()
}

void refresh(forceSchedule = false)
{
    // Update current state from fireplace.
    log.info "Refreshing status..."
    httpGet("http://${settings.ipAddress}/poll")
    { resp ->
        logDebug "Status ${resp.getStatus()}"        
        
        def jsonText = resp.data.text
        logDebug "$jsonText"
        
        def json = parseJson(jsonText)
        json.each
        { param, value ->
            //logDebug "Processing $param = $value"
            switch (param) {
                case "temperature":
                    // Need to rename the fireplace's raw attribute since it conflicts with TemperatureMeasurement's 'temperature'
                    sendEvent(name: "temperatureRaw", value: value, unit: "°C", description: "Raw fireplace poll data")
                
                    // TemperatureMeasurement
                    sendEvent(name: "temperature", value: convertCelsiusToUserTemperature(value), unit: "°${getTemperatureScale()}")
                    break

                case "fanspeed":
                    sendEvent(name: param, value: value, description: "Raw fireplace poll data")

                    // FanControl
                    sendEvent(name: "speed", value: FanControlSpeed[value])
                    break

                case "setpoint":
                    // This is actually celsius * 100...
                    sendEvent(name: param, value: value, unit: "°C * 100", description: "Raw fireplace poll data")
                
                    if (value != 0)
                    {
                        // Only update these events if we have a valid setpoint.  The app turns off the thermostat by setting it to 0, but
                        // we'll try to restore the previous setpoint automatically.
                        sendEvent(name: "setpointLast", value: value, unit: "°C * 100", description: "Last non-zero setpoint")

                        // ThermostatHeatingSetpoint
                        sendEvent(name: "heatingSetpoint", value: convertCelsiusToUserTemperature(value/100), unit: "°${getTemperatureScale()}")

                        // ThermostatSetpoint
                        sendEvent(name: "thermostatSetpoint", value: convertCelsiusToUserTemperature(value/100), unit: "°${getTemperatureScale()}")
                    }
                    break

                // Other events we may want to see and set.  Some are commented out to reduce event spam, since they aren't as useful or rarely change.
                case "light":                   // Fireplace light
                case "height":                  // Flame height
                case "pilot":                   // Cold-weather pilot light is enabled
                case "power":                   // Flame is on
                case "thermostat":              // Thermostate is controlling flame power
                case "timer":                   // Timer is activated
                case "timeremaining":           // Seconds until timer turns off fireplace
                //case "name":                  // Blank on my fireplace
                //case "serial":                // We already know this, and it doesn't change
                //case "battery":               // Emergency battery level (USB-C connection)
                //case "feature_light":         // Does this fireplace have a light?
                //case "feature_fan":           // Does this fireplace have a fan?
                //case "power_vent":            // unknown
                //case "errors":                // List of current errors reported by the fireplace?
                //case "fw_version":            // Numeric firmware version (not useful)
                //case "fw_ver_string":         // String firmware version
                //case "downtime":              // unknown
                //case "uptime":                // Time fireplace has been on internet
                //case "connection_quality":    // Connection quality of thermostat remote
                //case "ecm_latency":           // unknown
                //case "ipv4_address":          // We already know this.  Can't talk to the fireplace without it.                
                    sendEvent(name: param, value: value, description: "Raw fireplace poll data")
                    break
            }
        }

        // Switch
        // If 'thermostat' is on, it will toggle 'power' to turn the flame on and off according to room temperature.
        // From a practical control perspective, we should consider the fireplace to be "on" while the thermostat is
        // in control, regardless of actual flame state.
        def previousSwitchStatus = device.currentValue("switch")
        def switchStatus = (device.currentValue("power") || device.currentValue("thermostat")) ? "on" : "off"
        sendEvent(name: "switch", value: switchStatus, description:"power or thermostat is on")
        
        if (switchStatus != previousSwitchStatus || forceSchedule)
        {
            if (switchStatus == "on")
            {
                log.info "Increasing refresh frequency to every 5 minutes while fireplace is on."
                runEvery5Minutes("refresh")
            }
            else
            {
                log.info "Decreasing refresh frequency to every 15 minutes while fireplace is off."
                runEvery15Minutes("refresh")
            }
        }
    }
}

void setSpeed(String fanspeed)
{
    // Set the fan speed.
    int fanspeedInt = 0

    int fanspeedCount = FanControlSpeed.size()
    for (int i=0; i<fanspeedCount; i++)
    {
        if (fanspeed == FanControlSpeed[i])
        {
            fanspeedInt = i
        }
    }
        
    sendLocalCommand("FAN_SPEED", fanspeedInt)
}

void cycleSpeed()
{
    // Poll to get current value, then update to next value
    refresh()
    
    int newFanspeed = device.currentValue("fanspeed") + 1
    if (newFanspeed >= FanControlSpeed.size())
    {
        newFanspeed = 0
    }
    sendLocalCommand("FAN_SPEED", newFanspeed)
}

void on()
{
    if (thermostatOnDefault)
    {
        // Let's try to restore the last thermostat value, if we know it.
        setThermostatMode("on")
    }
    else
    {
        sendLocalCommand("POWER", 1)
    }
}

void off()
{
    // Turn off all modes.
    sendLocalCommand("POWER", 0)
}

void setHeatingSetpoint(temperature)
{
    // Set thermostat temperature
    def setpoint = convertUserTemperatureToCelsius(temperature) * 100
    
    sendLocalCommand("THERMOSTAT_SETPOINT", setpoint)
}

void beep()
{
    // Beep!  (...if it would actually beep.)
    sendLocalCommand("BEEP", 1)
}

void setPilotLight(enabled)
{
    // Enable/disable cold weather pilot light
    sendLocalCommand("PILOT", OnOffValue[enabled])
}

void setThermostatMode(enabled)
{
    // Enable/disable Thermostat mode
    refresh()
    def setPointValue = OnOffValue[enabled] ? device.currentValue("setpointLast") : 0
    sendLocalCommand("THERMOSTAT_SETPOINT", setPointValue)
}

void setLightLevel(level)
{
    // Set light level 0-3
    sendLocalCommand("LIGHT", level)
}

void setFlameHeight(level)
{
    // Set flame height 0-4
    sendLocalCommand("FLAME_HEIGHT", level)
}

void setTimer(minutes)
{
    // Set Sleep timer (up to 3 hours)
    // Convert to seconds before sending to fireplace.
    sendLocalCommand("TIME_REMAINING", minutes*60)
}

int convertCelsiusToUserTemperature(celsiusTemperature)
{
    if (getTemperatureScale() == "F")
    {
        return Math.round(celsiusToFahrenheit(celsiusTemperature))
    }
    else
    {
        return celsiusTemperature
    }
}

int convertUserTemperatureToCelsius(userTemperature)
{
    if (getTemperatureScale() == "F")
    {
        // The ECM or remote is truncating here, not rounding.
        // Copying that behavior here for consistency, as maddening as that is...
        return fahrenheitToCelsius(userTemperature)
    }
    else
    {
        return userTemperature
    }
}

def sendLocalCommand(command, value)
{
    def commandSpec = INTELLIFIRE_COMMANDS[command]

    if (value < commandSpec.min || value > commandSpec.max)
    {
        log.error "Command $command has value $value out of range [${commandSpec.min},${commandSpec.max}].  Ignoring..."
        return
    }

    log.info "Sending command ${commandSpec.localCommand} = $value"

    def commandData = "command=${commandSpec.localCommand}&value=$value"
    def payload = "post:$commandData"
    def apiKeyBytes = HexUtils.hexStringToByteArray(settings.apiKey)
    
    // TODO: Start retry loop here
    def challengeBytes = HexUtils.hexStringToByteArray(getChallenge())

    def digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.update(apiKeyBytes)
    digest.update(challengeBytes)
    digest.update(payload.getBytes())
    def payloadHash = digest.digest()
    
    digest.reset()
    digest.update(apiKeyBytes)
    digest.update(payloadHash)
    def response = HexUtils.byteArrayToHexString(digest.digest())

    def data = "$commandData&user=${settings.userId}&response=$response"
    def url = "http://${settings.ipAddress}/post"
        
    httpPost([
        uri: url,
        body: data,
        timeout: 5 // TODO - TBD
         ])
    { resp ->
        logDebug "Status ${resp.getStatus()}"        
        logDebug "Data: ${resp.data}"
    }
    
    //TODO: End retry loop

    // Force a refresh a few seconds after the command
    runIn(5, "refresh", [overwrite: true, data: [forceSchedule: true]])
}

def getChallenge()
{
    httpGet(uri: "http://${settings.ipAddress}/get_challenge")
    { resp ->
        logDebug "Status ${resp.getStatus()}"
        challenge = resp.data.text
        logDebug "Challenge $challenge"
    }

    return challenge
}

// Subset of officially supported FanControl speed names.
@Field FanControlSpeed =
[
    "off",
    "low",
    "medium",
    "medium-high",
    "high"
]

@Field Map OnOffValue =
[
	"off": 0,
	"on": 1
]

@Field
private static final INTELLIFIRE_COMMANDS =
[
    "POWER":
    [
        cloudCommand: "power",
        localCommand: "power",
        min: 0,
        max: 1
    ],
    "PILOT":
    [
        cloudCommand: "pilot",
        localCommand: "pilot",
        min: 0,
        max: 1
    ],
    "BEEP":
    [
        cloudCommand: "beep",
        localCommand: "beep",
        min: 1,
        max: 1,
    ],  // This doesn't actually seem to do anything
    "LIGHT":
    [
        cloudCommand: "light",
        localCommand: "light",
        min: 0,
        max: 3
    ],
    "FLAME_HEIGHT":
    [
        cloudCommand: "height",
        localCommand: "flame_height",
        min: 0,
        max: 4
    ],
    "FAN_SPEED":
    [
        cloudCommand: "fanspeed",
        localCommand: "fan_speed",
        min: 0,
        max: 4
    ],
    "THERMOSTAT_SETPOINT":
    [
        cloudCommand: "thermostat_setpoint",
        localCommand: "thermostat_setpoint",
        min: 0,
        max: 3700 // 37°C
    ],  // 0 = disable thermostat
    "TIME_REMAINING":
    [
        cloudCommand: "time_remaining",
        localCommand: "time_remaining",
        min: 0,
        max: 10800
    ],  // multiples of 60 - 0 = disable
    "SOFT_RESET":
    [  // This can be used to "soft reset the unit" -> probably dont ever need it.
        cloudCommand: "soft_reset",
        localCommand: "reset",  // Unaware of the local command for this one here
        min: 1,
        max: 1
    ]
]
