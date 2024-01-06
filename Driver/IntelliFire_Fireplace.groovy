/**
 *  IntelliFire Fireplace
 *
 *  Hubitat version created by Eric Will (corinuss)
 *
 *  Based heavily on 'intellifire4py' by jeeftor for Home Assistant.
 *  https://github.com/jeeftor/intellifire4py
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
 *    11/15/2023 v1.1.1   - Restored setOnOff.  It's needed for the Google Home Community integration.  Oops.
 *                          Fixed the description text in events.
 *    11/12/2023 v1.1.0   - Adding feature to enforce the previous fan setting is actually restored when turning on the fireplace.
 *                          Initial version of Light virtual device.
 *                          Made singleThreaded to attempt to reduce soft-locks possibly caused by simulanteous communications with fireplace.
 *                          Removed redundant setOnOff command.
 *    09/25/2023 v1.0.0   - Bumping version to 1.0.  Happy with this release.
 *    09/25/2023 v0.6.0   - Flame height controllable via SwitchLevel.
 *                          Google Home Community can now control the fireplace thermostat and fan.
 *    07/19/2022 v0.5.0   - Minor fixes to support the IntelliFire Fireplace Manager app and Google Home Community (WIP)
 *    07/04/2022 v0.4.0   - Error states now reported.  Also filter out temporarily-bad temperature data.
 *    07/03/2022 v0.3.0   - Automatic polling.
 *    07/03/2022 v0.2.0   - Added manual Polling.  Added a few missing commands.  On() can restore previous thermostat mode.  Code cleanup.
 *    06/22/2022 v0.1.0   - Initial publish.  Basic commands implemented but not stable.
 */

import groovy.transform.Field
import hubitat.helper.HexUtils
import java.security.MessageDigest

metadata
{
    definition (name: "IntelliFire Fireplace", namespace: "IntelliFire", author: "corinuss")
    {
        capability "FanControl"
        //capability "Light"      // Conflicts with "Switch".  Use a virtual device to handle the "Light" capability.
        capability "Refresh"
        capability "Switch"
        capability "SwitchLevel"
        capability "TemperatureMeasurement"
        capability "ThermostatHeatingSetpoint"
        //capability "ThermostatMode"   // Disabled because it adds too many buttons automatically.
        capability "ThermostatSetpoint"
        capability "Tone"
        
        command 'configure'
        command 'createVirtualLightDevice'
        //command 'lightOff'
        //command 'lightOn'
        command 'setFlameHeight', [[name: "Flame height (0-4)*", type:"NUMBER"]]
        command 'setLevel', [[name: "Flame height percentage (0-100)*", type:"NUMBER", description:"Percentage is mapped to discrete Flame Height values [0-4].  Used by SwitchLevel capability."]]
        //command 'setLightLevel', [[name: "Light level (0-3)*", type:"NUMBER"]]
        command 'setOnOff', [[name: "On/Off", type:"ENUM", description:"Turn the fireplace on or off. (Same effect as the separate 'on' and 'off' buttons.)", constraints: OnOffValue.collect {k,v -> k}]]
        command 'setPilotLight', [[name: "Cold Climate pilot light", type:"ENUM", description:"Enable the cold-weather pilot light?", constraints: OnOffValue.collect {k,v -> k}]]
        command 'setSpeed', [[name: "Fan speed", type:"ENUM", constraints: FanControlSpeed]]
        command 'setSpeedPercentage', [[name: "Fan speed percentage (0-100)*", type:"NUMBER", description:"Percentage is mapped to discrete Fan Speed values [0-4]."]]
        command 'setThermostatControl', [[name: "Thermostat Control", description:"Allow thermostat to control flame?", type:"ENUM", constraints: OnOffValue.collect {k,v -> k}]]
        command 'setTimer', [[name: "Timer (0-180)*", description:"Minutes until the fireplace turns off.  0 to disable.", type:"NUMBER"]]
        command 'softReset'

        attribute "errors", "string"
        attribute "fanspeed", "number"
        attribute "fanspeedPercent", "number"
        attribute "height", "number"
        attribute "level", "number"
        attribute "light", "number"
        attribute "pilot", "number"
        attribute "power", "number"
        attribute "thermostat", "number"
        attribute "thermostatMode", "enum", ["heat", "off"] // supported modes
        attribute "timer", "number"
    }
    
    preferences
    {
        input name: "ipAddress", type: "text", title: "Local IP Address", required: true
        input name: "apiKey", type: "text", title: "API Key", description: "Find this on IntelliFire's servers", required: true
        input name: "userId", type: "text", title: "User ID", description: "Find this on IntelliFire's servers", required: true
        input name: "thermostatOnDefault", type: "bool", title: "When turning on the fireplace, should the thermostat be enabled by default?", defaultValue: false
        input name: "shouldRestoreFanSpeed", type: "bool", title: "When turning on the fireplace, should we ensure the fan speed is restored to its last value?", defaultValue: false
        input name: "enableCloudControl", type: "bool", title: "Issue commands via online cloud API?", defaultValue: false
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

//
// INIT
//
void installed()
{
    configure()
}

void configure()
{
    cleanupDeprecatedSettings()

    sendEvent(name: "supportedFanSpeeds", value: FanControlSpeed)

    // Don't register refresh cycles until we have an IP address.
    if (settings.ipAddress != null)
    {
        refresh(forceSchedule: true)
    }
}

void cleanupDeprecatedSettings()
{
    // Removed in 2.0.0
    device.deleteCurrentState('fanspeedpercent')
    device.deleteCurrentState('feature_light')
    device.deleteCurrentState('setpoint')
    device.deleteCurrentState('temperatureRaw')
    device.deleteCurrentState('timeremaining')

    if (device.hasAttribute('fanspeedLast'))
    {
        state.fanspeedLast = device.getCurrentState('fanspeedLast')
        device.deleteCurrentState('fanspeedLast')
    }

    if (device.hasAttribute('lightLast'))
    {
        state.lightLast = device.getCurrentState('lightLast')
        device.deleteCurrentState('lightLast')
    }

    if (device.hasAttribute('setpointLast'))
    {
        state.setpointLast = device.getCurrentState('setpointLast')
        device.deleteCurrentState('setpointLast')
    }

    if (device.hasAttribute('serial'))
    {
        state.serial = device.getCurrentState('serial')
        device.deleteCurrentState('serial')
    }
}

void setSerial(serial)
{
    // Pre-populate the serial number if it was provided to us.
    // Guarantees we have it in case the fireplace isn't available during initialization from the Manager app.
    state.serial = serial
}

void updateCloudControl()
{
    if (state.isUsingCloud != settings.enableCloudControl)
    {
        state.isUsingCloud = settings.enableCloudControl

        if (state.isUsingCloud)
        {
            unschedule("refresh")
            cloudPoll()
            runEvery1Minute("cloudLongPollMonitor")
        }
        else
        {
            unschedule("cloudLongPollMonitor")
            localPoll()
        }
    }
}

void notifyLoginChange(loginUniqueId)
{
    // TODO - Figure out how to do this correctly.
    // Scenarios:
    // * Everything is running well, and login has changed.
    //  * If we do nothing, it'll fix itself on the next refresh.
    // * Polling has stopped because credentials were bad.
    //  * Restart polling by calling cloudPoll().
    // * Polling has stopped unexpectedly.  Monitor hasn't started it yet.
    //  * Restart polling by calling cloudPoll().  Make sure monitor doesn't duplicate efforts.  synchronization?
    //
    // Other questions
    // Do we need to save off the uniqueLoginId before starting an async cycle?  If we assume only one polling at a time,
    // can we signal it's been interrupted?
    // How do we prevent a race condition between the monitor and this?
    if (state.isUsingCloud)
    {
        cloudPoll()
    }
}

//
// REFRESH (POLLING)
//
void refresh(forceSchedule = false)
{
    if (!state.isUsingCloud)
    {
        return localPoll()
    }
}

void localPoll(forceSchedule = false)
{
    def previousSwitchStatus = device.currentValue("switch")

    // Update current state from fireplace.
    log.info "Refreshing status..."
    synchronized(this)
    {
        httpGet("http://${settings.ipAddress}/poll")
        { resp ->
            logDebug "Status ${resp.getStatus()}"
            consumeStatus(resp.data)
        }
    }

    def switchStatus = device.currentValue("switch", true)
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

    updateCloudControl()
}

void cloudPoll()
{
    // TODO - If login credentials are bad, stop?
    def loginUniqueId = parent.getCurrentLoginId()
    state.cloudPollTimestamp = getTime()
    httpGet([
            uri: "${parent.getRemoteServerRoot()}/$serial/apppoll",
            headers: [ 'Cookie': parent.makeCookiesString(loginUniqueId) ],
        ])
    { resp ->
        logDebug "apppoll Status ${resp.getStatus()}"
        consumeStatus(resp.data)
    }

    cloudLongPollStart(loginUniqueId)
}

void cloudLongPollStart(loginUniqueId)
{
    if (state.cloudLongPollActive)
    {
        log.warning "There is a cloud long poll currently in progress.  Not starting another long poll."
        return
    }

    def serial = state.serial
    def cookies = parent.makeCookiesString(loginUniqueId)
    if (cookies == null)
    {
        // Login credentials have changed or are no longer valid.  Try a normal poll again.
        cloudPoll()
    }
    else
    {
        state.cloudLongPollActive = true
        state.cloudPollTimestamp = getTime()
        asynchttpGet(
            longPollResult,
            [
                uri: "${parent.getRemoteServerRoot()}/$serial/applongpoll",
                headers: [ 'Cookie': parent.makeCookiesString(loginUniqueId) ],
                timeout: 63
            ],
            [ loginUniqueId: loginUniqueId ])
    }
}

void cloudLongPollResult(data)
{
    // Common ways this function ends:
    // * Data changed
    //      Status is 200 and headers contain a unique Etag.
    //      Reaction: Issue a new long poll and send the Etag back.
    // * Long Poll timeout
    //      Status is 408 and headers contain a default Etag ("0:0")
    //      Reaction: Issue a new long poll without the Etag to resume.
    // * Connection died
    //      Status is 408 and headers do NOT contain an Etag.
    //      Reaction: Restart polling with a normal poll first.
    // * Other unexpected error
    //      Status is anything else.
    //      Reaction: Restart polling with a normal poll first.

    state.cloudLongPollActive = false
    logDebug "longPollResult Status ${resp.getStatus()}"    

    if (resp.getStatus() == 200)
    {
        consumeStatus(resp.data)
    }

    // Check to see if we've switched to local polling before sending another request.
    updateCloudControl()

    if (state.isUsingCloud)
    {
        def isExpectedResponse = false
        def outgoingHeaders = [ 'Cookie': parent.makeCookiesString(data[loginUniqueId]) ]

        if (resp.getStatus() == 200 || resp.getStatus() == 408)
        {
            // The connection dying looks a lot like a normal timeout, so look to see if the cloud sent us an Etag.
            // If we don't see one, then the connection was terminated by us rather than the cloud.
            def incomingHeaders = resp.getHeaders()
            if (incomingHeaders != null && incomingHeaders.containsKey('Etag'))
            {
                // This is normal behavior, so continue with long polls.
                isExpectedResponse = true

                if (resp.getStatus() == 200)
                {
                    // Need to send the Etag back to avoid getting the same data again.
                    def etag = incomingHeaders['Etag']
                    outgoingHeaders['If-None-Match'] = etag
                    logDebug "Etag = $etag"
                }
            }
        }

        def cookies = parent.makeCookiesString(loginUniqueId)
        if (isExpectedResponse && cookies != null)
        {
            def serial = state.serial

            state.cloudLongPollActive = true
            state.cloudPollTimestamp = getTime()
            asynchttpGet(
                longPollResult,
                [
                    uri: "${parent.getRemoteServerRoot()}/$serial/applongpoll",
                    headers: outgoingHeaders,
                    timeout: 63
                ])
        }
        else
        {
            if (!isExpectedResponse)
            {
                log.warning "Long Poll failed with status ${resp.getStatus()}.  Trying a regular poll."
            }

            def retryDelayMilliseconds = 60000 - (getTime() - state.cloudPollTimestamp)
            if (retryDelayMilliseconds > 0)
            {
                runInMillis(retryDelayMilliseconds, "cloudPoll")
            }
            else
            {
                cloudPoll()
            }
        }
    }
}

void cloudLongPollMonitor()
{
    if (state.isUsingCloud && parent.makeCookiesString() != null)
    {
        // If we haven't had a successfull long poll for a while,
        // do a full poll reset.
        def currentTime = getTime()

        if (state.cloudPollTimestamp + 180000 < currentTime)
        {
            log.warning "Cloud long polling appears to have stalled.  cloudLongPollActive(${state.cloudLongPollActive}). Restarting..."

            state.cloudLongPollActive = false
            cloudPoll()
        }
    }
}

void consumeStatus(statusMap)
{                    
    // To set the Switch status properly, we need to store the new power and thermostat status
    // within this function, since events don't immediately apply.  May as well intialize them
    // with current status, even though they should always be set during refresh...
    def powerStatus = device.currentValue("power")
    def thermostatStatus = device.currentValue("thermostat")

    logDebug "$statusMap"
    statusMap.each
    { param, value ->
        //logDebug "Processing $param = $value"
        switch (param) {
            case "temperature":
                // Thermostat data sometimes cuts out, so only send temperature events if the data is valid...
                if (statusMap.get("feature_thermostat", 0) != 0)
                {
                    // TemperatureMeasurement
                    sendEvent(name: "temperature", value: convertCelsiusToUserTemperature(value), unit: "°${getTemperatureScale()}")
                }
                break

            case "fanspeed":
                sendEvent(name: param, value: value, descriptionText: "Raw fireplace poll data")

                // FanControl
                sendEvent(name: "speed", value: FanControlSpeed[value])

                // Google Fan Speed Percentages
                sendEvent(name: "fanspeedPercent", value: value*25, unit: "%", descriptionText: "Fan speed")

                if (value != 0)
                {
                    // Save off the currently set fan speed, but only if non-zero.
                    // Captures current fan speed if it was set by another control mechanism (remote or mobile app)
                    state.fanspeedLast = value
                }
                break

            case "setpoint":
                // This is actually celsius * 100...
                if (value != 0)
                {
                    // Only update these events if we have a valid setpoint.  The app turns off the thermostat by setting it to 0, but
                    // we'll try to restore the previous setpoint automatically.
                    state.setpointLast = value

                    // ThermostatHeatingSetpoint
                    sendEvent(name: "heatingSetpoint", value: convertCelsiusToUserTemperature(value/100), unit: "°${getTemperatureScale()}")

                    // ThermostatSetpoint
                    sendEvent(name: "thermostatSetpoint", value: convertCelsiusToUserTemperature(value/100), unit: "°${getTemperatureScale()}")
                }
                break
                
            case "height":
                sendEvent(name: param, value: value, descriptionText: "Raw fireplace poll data")

                // SwitchLevel
                sendEvent(name: "level", value: value*25, unit: "%", descriptionText: "Flame height")
                break

            case "errors":
                // First convert the error integers into short error code strings for our attributes.
                def errorList = []
                value.each { errorInt -> errorList << ERROR_MESSAGE_VALUE_MAP[errorInt] }
                sendEvent(name: "errors", value: errorList)
                
                // Now output the error messages to the log.
                errorList.each { errorCode -> log.error "${ERROR_MESSAGES[errorCode]}" }
                break

            // Flame is on
            case "power":
                powerStatus = value
                sendEvent(name: param, value: value, descriptionText: "Raw fireplace poll data")
                break;

            // Thermostat is controlling flame power
            case "thermostat":
                thermostatStatus = value
                sendEvent(name: param, value: value, descriptionText: "Raw fireplace poll data")
                break;

            case "light":
                sendEvent(name: param, value: value, descriptionText: "Raw fireplace poll data")

                if (value != 0)
                {
                    // Only save the light value if it's not off.  Used to restore the light when
                    // issued a simple "lightOn" request.
                    state.lightLast = value
                }

                // Notify any child devices implementing Light control
                try
                {
                    getChildDevices()?.each
                    {
                        if (it.hasCapability("Light"))
                        {
                            it.setLightLevelFromParent(value)
                        }
                    }
                } catch (err) {
                    logDebug "Either no children exist or error finding child devices for some reason: ${err}"
                }
                break

            case "ipv4_address":
                if (settings.ipAddress != value)
                {
                    log.info "Updating ipAddress to $value"
                    settings.ipAddress = value
                }
                break

            // Device unique serial (used for identification)
            case "serial":
                if (state.serial != value)
                {
                    state.serial = value
                }
                break

            // Does this fireplace have a light?
            case "feature_light":
                state.hasLight = (value != 0)
                break

            // Other events we may want to see and set.  Some are commented out to reduce event spam, since they aren't as useful or rarely change.
            case "pilot":                   // Cold-weather pilot light is enabled
            case "timer":                   // Timer is activated
            //case "timeremaining":         // Seconds until timer turns off fireplace (doesn't get updated frequently enough)
            //case "name":                  // Blank on my fireplace
            //case "battery":               // Emergency battery level (USB-C connection)
            //case "feature_thermostat":    // Does this fireplace have a thermostat and temperature data?
            //case "power_vent":            // Does this fireplace have a power vent?
            //case "feature_fan":           // Does this fireplace have a fan?
            //case "fw_version":            // Numeric firmware version (not useful)
            //case "fw_ver_string":         // String firmware version
            //case "downtime":              // unknown
            //case "uptime":                // Time fireplace has been on internet
            //case "connection_quality":    // Connection quality of thermostat remote
            //case "ecm_latency":           // unknown
            //case "ipv4_address":          // We already know this.  Can't talk to the fireplace without it.
                sendEvent(name: param, value: value, descriptionText: "Raw fireplace poll data")
                break
        }
    }

    // Switch
    // If 'thermostat' is on, it will toggle 'power' to turn the flame on and off according to room temperature.
    // From a practical control perspective, we should consider the fireplace to be "on" while the thermostat is
    // in control, regardless of actual flame state.
    def previousSwitchStatus = device.currentValue("switch")
    def switchStatus = (powerStatus || thermostatStatus)
    sendEvent(name: "switch", value: switchStatus ? "on" : "off", descriptionText:"power or thermostat is on")

    // ThermostatMode
    // We've tied "heat" vs "off" to the switch value.
    sendEvent(name: "thermostatMode", value: switchStatus ? "heat" : "off")
}

//
// FAN SPEED
//

// Google Home Community
void setSpeedPercentage(fanspeedPercentage)
{
    int fanspeed = (int)((fanspeedPercentage+24)/25);
    setSpeedInternal(fanspeed)
}

// FanControl
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

    setSpeedInternal(fanspeedInt)
}

void setSpeedInternal(int fanspeed)
{
    // Explicitly set Last fan speed here.
    // Ensures that if we turn off the fan, we don't try to restore it later.
    // Also ensures we save the fan speed changes while the flame is currently off due to thermostat control.
    state.fanspeedLast = 0
    
    sendCommand("FAN_SPEED", fanspeed)
}

void restoreFanSpeed()
{
    int fanspeedLast = state.fanspeedLast ?: 0
    if (fanspeedLast != 0)
    {
        logDebug "Previous fan speed $fanspeedLast saved.  Checking current fan speed to see if restoration is needed."

        refresh()
        if (device.currentValue("fanspeed", true) == 0)
        {
            log.info "Restoring fan speed to $fanspeedLast"
            setSpeedInternal(fanspeedLast)
        }
    }
}

// FanControl
void cycleSpeed()
{
    // Poll to get current value, then update to next value
    refresh()
    
    int newFanspeed = (device.currentValue("fanspeed", true) ?: 0) + 1
    if (newFanspeed >= FanControlSpeed.size())
    {
        newFanspeed = 0
    }

    setSpeedInternal(newFanspeed)
}

//
// SWITCH
//

// Switch
void on()
{
    if (shouldRestoreFanSpeed)
    {
        logDebug "Will check fan speed in 5 minutes."
        runIn(5*60, "restoreFanSpeed", [overwrite: true])
    }

    if (thermostatOnDefault)
    {
        // Let's try to restore the last thermostat value, if we know it.
        setThermostatControl("on")
    }
    else
    {
        sendCommand("POWER", 1)
    }
}

// Switch
void off()
{
    unschedule("restoreFanSpeed")

    // Turn off all modes.
    sendCommand("POWER", 0)
}

//
// THERMOSTAT CONTROL
//
void setThermostatMode(thermostatmode)
{
    if (thermostatmode == "heat")
    {
        on()
    }
    else
    {
        off()
    }
}

// ThermostatHeatingSetpoint
void setHeatingSetpoint(temperature)
{
    // Set thermostat temperature
    def setpoint = convertUserTemperatureToCelsius(temperature) * 100
    sendCommand("THERMOSTAT_SETPOINT", setpoint)
}

void setThermostatControl(enabled)
{
    // Enable/disable Thermostat mode
    refresh()
    int setPointValue = 0
    if (OnOffValue[enabled])
    {
        // If not set (such as first run), set this to something reasonable so the flame comes on if the room is cold.
        // Default value on the remote is 72F (22C).
        setPointValue = state.setpointLast ?: 2200
    }

    logDebug "setThermostatControl THERMOSTAT_SETPOINT $setPointValue"
    sendCommand("THERMOSTAT_SETPOINT", setPointValue)
}

//
// LIGHT
//
void createVirtualLightDevice(overrideHasLight = false)
{
    if (!overrideHasLight && state.hasLight != 1)
    {
        log.warn "Fireplace reports Light feature not available.  Aborting child Light creation."
        return
    }

    def serial = state.serial
    if (serial == null)
    {
        log.error "No serial available.  Cannot create child Light device."
        return
    }

    def fireplaceLightDni = "IntelliFireLight-$serial"

    def childDevice = getChildDevice(fireplaceLightDni)
    if (childDevice == null)
    {
        def myLabel = device.getLabel()
        def childLabel = "$myLabel Light"

        log.info "Creating new Light child device $childLabel"
        addChildDevice("IntelliFire", "IntelliFire Fireplace Virtual Light", fireplaceLightDni, [label: childLabel])
    }
    else
    {
        log.info "Device '${childDevice.getLabel()}' already exists.  Not creating a new Light child device."
    }

    // Refresh to allow the new device to get current status.
    refresh()
}

void lightOn()
{
    // Restore the previous light level.
    def lightLevel = state.lightLast
    if (lightLevel == null)
    {
        // If not yet set, set to max.
        lightLevel = INTELLIFIRE_COMMANDS["LIGHT"].max
    }
    
    setLightLevel(lightLevel)
}

// Light (via Light virtual device)
void lightOff()
{
    setLightLevel(0)
}

//
// OTHER COMMANDS
//

// SwitchLevel (via Light virtual device)
void setLightLevel(level)
{
    // Set light level 0-3
    sendCommand("LIGHT", level)
}

void beep()
{
    // Beep!  (...if it would actually beep.)
    sendCommand("BEEP", 1)
}

void setPilotLight(enabled)
{
    // Enable/disable cold weather pilot light
    sendCommand("PILOT", OnOffValue[enabled])
}

// SwitchLevel
void setLevel(level, duration = 0)
{
    // 'duration' is not used

    // Map the percentage to our flame height levels.
    //  0    = flame height 0
    //  1-25 = flame height 1
    // 26-50 = flame height 2
    // ...
    def flameHeight = (int)((level+24)/25);
    setFlameHeight(flameHeight);
}

void setFlameHeight(flameHeight)
{
    // Set flame height 0-4
    sendCommand("FLAME_HEIGHT", flameHeight)
}

void setTimer(minutes)
{
    // Set Sleep timer (up to 3 hours)
    // Convert to seconds before sending to fireplace.
    sendCommand("TIME_REMAINING", minutes*60)
}

void softReset()
{
    sendCommand("SOFT_RESET", 1)
}

//
// UTILITY
//
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

//
// SEND COMMAND
//
def sendCommand(command, value)
{
    if (state.isUsingCloud)
    {
        return sendCloudCommand(command, value)
    }
    else
    {
        return sendLocalCommand(command, value)
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

    log.info "Sending local command ${commandSpec.localCommand} = $value"

    def commandData = "command=${commandSpec.localCommand}&value=$value"
    def payload = "post:$commandData"
    def apiKeyBytes = HexUtils.hexStringToByteArray(settings.apiKey)
    
    synchronized(this)
    {
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
            timeout: 5
        ])
        { resp ->
            logDebug "Status ${resp.getStatus()}"        
            logDebug "Data: ${resp.data}"
        }
    }

    if (!state.isUsingCloud)
    {
        // Force a refresh a few seconds after the command.
        // This needs to be short enough so Google can get a response before timing out,
        // but long enough to not soft-lock the fireplace.
        runIn(3, "refresh", [overwrite: true, data: [forceSchedule: true]])
    }
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

def sendCloudCommand(command, value, isRetry = false)
{
    // TODO - Abort if not signed in.
    def success= false
    def commandSpec = INTELLIFIRE_COMMANDS[command]
    def serial = state.serial

    if (value < commandSpec.min || value > commandSpec.max)
    {
        log.error "Command $command has value $value out of range [${commandSpec.min},${commandSpec.max}].  Ignoring..."
        return
    }

    log.info "Sending cloud command ${commandSpec.cloudCommand} = $value"

    try
    {
        httpPost([
                uri: "${parent.getRemoteServerRoot()}/$serial/${settings.apiKey}/apppost",
                headers: [ 'Cookie': parent.makeCookiesString() ],
                body: "${commandSpec.cloudCommand}=$value",
                timeout: 10
            ])
        { resp ->
            def responseStatus = resp.status
            logDebug "Status $responseStatus"
            logDebug "Data: ${resp.data}"
            success = true
        }
    }
    catch (HttpResponseException e)
    {
        def statusCode = e.getStatusCode()

        if (statusCode == 403)
        {
            log.error "Failed while issuing command '${commandSpec.cloudCommand}' due to invalid credentials.  Attempting to refresh."
            parent.refreshCredentials()

            if (!isRetry)
            {                
                sendCloudCommand(command, value, true)
            }
        }
        else
        {
            log.error "Failed while issuing command '${commandSpec.cloudCommand}': Response $statusCode"
        }

        success = false
    }
    catch (e)
    {
        log.error "Failed while issuing command '${commandSpec.cloudCommand}': Exception $e"
        success = false
    }

    return success
}

//
// GLOBAL CONSTANTS
//

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

@Field
private static final ERROR_MESSAGE_VALUE_MAP =
[
    2:    "PILOT_FLAME",
    4:    "FLAME",
    6:    "FAN_DELAY",
    64:   "MAINTENANCE",
    129:  "DISABLED",
    130:  "PILOT_FLAME",
    132:  "FAN",
    133:  "LIGHTS",
    134:  "ACCESSORY",
    144:  "SOFT_LOCK_OUT",
    145:  "DISABLED",
    642:  "OFFLINE",
    3269: "ECM_OFFLINE"
].withDefault { otherError -> "$otherError" }

@Field
private static final ERROR_MESSAGES =
[
    "PILOT_FLAME":   "Pilot Flame Error: Your appliance has been safely disabled. Please contact your dealer and report this issue.",
    "FAN_DELAY":     "Fan Information: Fan will turn on within 3 minutes. Your appliance has a built-in delay that prevents the fan from operating within the first 3 minutes of turning on the appliance. This allows the air to be heated prior to circulation.",
    "FLAME":         "Pilot Flame Error. Your appliance has been safely disabled. Please contact your dealer and report this issue.",
    "MAINTENANCE":   "Maintenance: Your appliance is due for a routine maintenance check. Please contact your dealer to ensure your appliance is operating at peak performance.",
    "DISABLED":      "Appliance Safely Disabled: Your appliance has been disabled. Please contact your dealer and report this issue.",
    "FAN":           "Fan Error. Your appliance has detected that an accessory is not functional. Please contact your dealer and report this issue.",
    "LIGHTS":        "Lights Error. Your appliance has detected that an accessory is not functional. Please contact your dealer and report this issue.",
    "ACCESSORY":     "Your appliance has detected that an AUX port or accessory is not functional. Please contact your dealer and report this issue.",
    "SOFT_LOCK_OUT": "Sorry your appliance did not start. Try again by pressing Flame ON.",
    "OFFLINE":       "Your appliance is currently offline.",
    "ECM_OFFLINE":   "ECM is offline.",
].withDefault { otherError -> "Unknown Error. ($otherError)" }
