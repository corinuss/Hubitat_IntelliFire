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
 *    05/10/2020 v0.1.0   - Initial publish
 *
 */

import groovy.transform.Field
import java.security.MessageDigest
import hubitat.helper.HexUtils

metadata {
    definition (name: 'IntelliFire Fireplace', namespace: 'IntelliFire', author: 'Eric Will') {
        capability "FanControl"
        //capability "Light"
        capability "Polling"
        capability "Refresh"
        capability "Switch"
        capability "TemperatureMeasurement"
//        capability "Thermostat"
//        capability "ThermostatFanMode"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatSetpoint"
        capability "Tone"
        
        command 'configure'
        command 'setSpeed', [[name: "Fan speed", type:"ENUM", constraints: FanControlSpeed.collect {k,v -> k}]]
        command 'setPilotLight', [[name: "Pilot light", type:"ENUM", description:"Enable the cold-weather pilot light?", constraints: PilotLightLevel.collect {k,v -> k}]]
        command 'setLightLevel', [[name: "Light level (0-3)", type:"NUMBER"]]
        command 'setFlameHeight', [[name: "Flame height (0-4)", type:"NUMBER"]]
        command 'setTimer', [[name: "Timer (0-180)", description:"Minutes until the fireplace turns off.  0 to disable.", type:"NUMBER"]]
        command 'setTemperatureScale', [[name: "Timer (0-180)", description:"Minutes until the fireplace turns off.  0 to disable.", type:"NUMBER"]]
        
//        command 'cycleSpeed'
//        command 'on'
//        command 'off'
//        command 'poll'
//        command 'refresh'
//        command 'setfanspeed', ["number"]
//        command 'cyclespeed'
//        command 'setHeatingSetpoint', ["number"]
//        command 'refresh'
        
//        attribute "speed", "enum", ["low", /*"medium-low",*/ "medium","medium-high","high","on","off" /*,"auto"*/]
//        attribute "switch", "enum", ["on","off"]
        
//        attribute "heatingSetpoint", "number"
//        attribute "thermostatSetpoint", "number"
//        attribute "temperature", "number"

//        attribute "supportedFanSpeeds", "JSON_OBJECT" // No idea.  Forums say maybe set this dynamically during init?
//        attribute "supportedThermostatFanModes", "JSON_OBJECT" // No idea.  Forums say maybe set this dynamically during init?
//        attribute "supportedThermostatModes", "JSON_OBJECT" // No idea.  Forums say maybe set this dynamically during init?
//        thermostatFanMode - ENUM ["on", "circulate", "auto"]
//        attribute "thermostatMode", "enum", [/*"auto",*/ "off", "heat"/*, "emergency heat", "cool"*/]
//        attribute "thermostatOperatingState", "enum", ["heating", /*"pending cool", "pending heat", "vent economizer",*/ "idle", /*"cooling",*/ "fan only"]
    }
    
    preferences {
        input name: "ip_address", type: "text", title: "Local IP Address", description: "Needed to find the fireplace", required: true
        input name: "apikey", type: "text", title: "API Key", description: "Pulled from Intellifire's servers", required: true
        input name: "user_id", type: "text", title: "User ID", description: "Pulled from Intellifire's servers", required: true
    }
}

void installed()
{
    configure()
}

void configure()
{
    sendEvent(name: "supportedFanSpeeds", value: FanControlSpeed.collect {k,v -> k})
}

void poll()
{
    refresh()
}

void refresh()
{
    // Update current state from fireplace.
    log.info "Polling..."
    httpGet("http://${settings.ip_address}/poll")
    { resp ->
        log.info "Status ${resp.getStatus()}"        
        log.info "Data: ${resp.data}"
    }
}

void setSpeed(fanspeed)
{
    // Set the fan speed.
    sendLocalCommand("FAN_SPEED", FanControlSpeed[fanspeed])
}

void cycleSpeed()
{
    // Cycle fan speed
}

void on()
{
    // Turn on to last mode?
    sendLocalCommand("POWER", 1)
}

void off()
{
    // Turn off all modes.
    sendLocalCommand("POWER", 0)
}

void setHeatingSetpoint(temperature)
{
    // Set thermostat temperature
    def tempurature_c = convertUserTemperatureToCelsius(temperature)
    
    // TODO: Move this to the result of a poll?
    def tempurature_u = convertCelsiusToUserTemperature(tempurature_c)    
	sendEvent(name: 'heatingSetpoint', value: tempurature_u, unit: getTemperatureScale())
}

void beep()
{
    // Beep!
    sendLocalCommand("BEEP", 1)
}

void setPilotLight(enabled)
{
    // Enable/disable cold weather pilot light
    sendLocalCommand("PILOT", PilotLightLevel[enabled])
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

Integer convertCelsiusToUserTemperature(temperature_c)
{
    def temperature = temperature_c
    if (getTemperatureScale() == "F")
    {
        temperature = Math.round(celsiusToFahrenheit(temperature_c))
    }
    
    return temperature
}

Integer convertUserTemperatureToCelsius(temperature_u)
{
    def temperature = temperature_u
    if (getTemperatureScale() == "F")
    {
        temperature = Math.round(fahrenheitToCelsius(temperature_u));
    }
    
    return temperature
}

def sendLocalCommand(command, value)
{
    def commandSpec = INTELLIFIRE_COMMANDS[command]

    if (value < commandSpec.min || value > commandSpec.max)
    {
        log.error "Command $command has value $value out of range [${commandSpec.min},${commandSpec.max}].  Ignoring..."
        return
    }

    log.info "Sending command ${commandSpec.local_command} = $value"

    def payload = "post:command=${commandSpec.local_command}&value=$value"
    def payload_bytes = payload.getBytes("UTF-8")
    
    def api_bytes = HexUtils.hexStringToByteArray(settings.apikey)
    
    // TODO: Start retry loop here
    def challenge = getChallenge()
    def challenge_bytes = HexUtils.hexStringToByteArray(challenge)

    def digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.update(api_bytes)
    digest.update(challenge_bytes)
    digest.update(payload_bytes)
    def payload_hash_bytes = digest.digest()
    
    digest.reset()
    digest.update(api_bytes)
    digest.update(payload_hash_bytes)
    def response = HexUtils.byteArrayToHexString(digest.digest())

    def data = "command=${commandSpec.local_command}&value=$value&user=${settings.user_id}&response=${response.toLowerCase()}"
    def url = "http://${settings.ip_address}/post"
        
    httpPost([
        uri: url,
        body: data,
        contentType: "application/x-www-form-urlencoded",
        timeout: 5 // TBD
         ])
    { resp ->
        log.info "Status ${resp.getStatus()}"        
        log.info "Data: ${resp.data}"
    }
    
    //TODO: End retry loop
}

def getChallenge() {    
    httpGet(uri: "http://${settings.ip_address}/get_challenge")
    { resp ->
        log.info "Status ${resp.getStatus()}"
        challenge = resp.data.text
        log.info "Challenge $challenge"
    }

    return challenge
}

// Subset of officially supported FanControl speed names.
@Field Map FanControlSpeed = [
	"off": 0,
	"low": 1,
	"medium": 2,
	"medium-high": 3,
	"high": 4
]

@Field Map PilotLightLevel = [
	"off": 0,
	"on": 1
]

@Field
private static final INTELLIFIRE_COMMANDS = [
    "POWER": [
        cloud_command: "power",
        local_command: "power",
        min: 0,
        max: 1
    ],
    "PILOT": [
        cloud_command: "pilot",
        local_command: "pilot",
        min: 0,
        max: 1
    ],
    "BEEP": [
        cloud_command: "beep",
        local_command: "beep",
        min: 1,
        max: 1,
    ],  // This doesn't actually seem to do anything
    "LIGHT": [
        cloud_command: "light",
        local_command: "light",
        min: 0,
        max: 3
    ],
    "FLAME_HEIGHT": [
        cloud_command: "height",
        local_command: "flame_height",
        min: 0,
        max: 4
    ],
    "FAN_SPEED": [
        cloud_command: "fanspeed",
        local_command: "fan_speed",
        min: 0,
        max: 4
    ],
    "THERMOSTAT_SETPOINT": [
        cloud_command: "thermostat_setpoint",
        local_command: "thermostat_setpoint",
        min: 0,
        max: 3700
    ],  // 0 = disable thermostat
    "TIME_REMAINING": [
        cloud_command: "time_remaining",
        local_command: "time_remaining",
        min: 0,
        max: 10800
    ],  // multiples of 60 - 0 = disable
    "SOFT_RESET": [  // This can be used to "soft reset the unit" -> probably dont ever need it.
        cloud_command: "soft_reset",
        local_command: "soft_reset",  // Unaware of the local command for this one here
        min: 1,
        max: 1
    ]
]
