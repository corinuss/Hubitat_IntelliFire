/**
 *  IntelliFire Fireplace Manager
 *
 *  Created by Eric Will (corinuss)
 *
 *  Allows a user to create local Hubitat IntelliFire fireplace devices using
 *  information queried from the IntelliFire servers.
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
 *    09/25/2023 v1.0.0   - Bumping version to 1.0.  Happy with this release.
 *    09/25/2023 v0.6.0   - Save (and forget) website credentials
 *    07/19/2022 v0.5.0   - Initial version (Add a fireplace)
 */

import groovy.transform.Field
import org.apache.http.client.HttpResponseException

@Field final String SERVER_ROOT = "https://iftapi.net/a"

@Field static String statusMessage = ""
@Field static def fireplaceLocations = [:]
@Field static def fireplacesInfo = [:]

definition(
    name: "IntelliFire Fireplace Manager",
    namespace: "IntelliFire",
    author: "corinuss",
    description: "Uses IntelliFire servers to create and update IntelliFire Fireplace devices",
    category: "Integrations",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
    section("Title") {
        page(name: "mainPage")
        page(name: "loginPage")
        page(name: "loginResultPage")
        page(name: "locationsPage")
        page(name: "fireplacesPage")
        page(name: "createResultsPage")
    }
}

void logDebug (msg)
{
    if (logEnable)
    {
        log.debug msg
    }
}

def addStatusMessage(msg)
{
    if (statusMessage == null)
        statusMessage = ""
    statusMessage += "${msg}<br>"
}

def clearStatusMessage()
{
    statusMessage = ""
}

def getStatusMessage()
{
    return statusMessage
}

def mainPage()
{
    logDebug "mainPage"
    
    dynamicPage(name: "mainPage", nextPage: "", uninstall: false, install: true)
    {
        section("")
        {
            href "loginPage", title: "Add Fireplace(s)", description: "Add a new fireplace or refresh an existing fireplace from online settings"
            input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
        }
    }
}

def loginPage()
{
    logDebug "loginPage"
    
    return dynamicPage(name: "loginPage", title: "Connect to IntelliFire Servers", nextPage: "loginResultPage" /*, uninstall: false, install: false, submitOnChange: true*/) {
        section("Login Credentials")
        {
            paragraph "We need to obtain some information from IntelliFire's servers to allow us to gain local access to the fireplace.  Once the fireplace is set up, all future communication will be done locally."
            paragraph "Before continuing, please ensure that you can access your fireplace(s) from the IntelliFire mobile app, and that you've placed your fireplace(s) on a static IP.  (Use your router's DHCP to reserve an IP for the fireplace.)"
            paragraph "Please provide IntelliFire login credentials."
            input(name: "username", type: "email", title: "Username", description: "IntelliFire Username (email address)")
            input(name: "password", type: "password", title: "Password", description: "IntelliFire Password")
            input(name: "saveCredentials", type: "bool", title: "Save Credentials?", required: true, defaultValue: true)
        }
    }
}


def loginResultPage()
{
    logDebug "loginResultPage"

    if (doLogin())
    {
        return locationsPage()
    }
    else
    {
        return dynamicPage(name: "loginResultPage", title: "Login Error") {
            section("")
            {
                paragraph "The username or password you entered is incorrect."
            }
        }
    }
}

def locationsPage(params)
{
    logDebug "locationsPage"
    
    getLocations()
    
    return dynamicPage(name: "locationsPage", title: "Location", nextPage: "fireplacesPage") {
        section("Choose your location") {
            input(name: "location", title: "Locations", type: "enum", required: true, multiple: false, options: fireplaceLocations)
        }
    }
}

def fireplacesPage(params)
{
    logDebug "fireplacesPage"
    
    getFireplaces(settings.location)
    
    return dynamicPage(name: "fireplacesPage", title: "Fireplaces", nextPage: "createResultsPage") {
        section("Choose fireplaces to add or refresh") {
            input(name: "fireplaces", title: "Fireplaces", type: "enum", required: true, multiple: true, options: getFireplacesList())
        }
    }
}

def createResultsPage(params)
{
    logDebug "fireplaces: $fireplacesInfo"

    getFireplaceInfos(settings.fireplaces)

    return dynamicPage(name: "createResultsPage", title: "Results", nextPage: "mainPage") {
        section {
            paragraph getStatusMessage()
        }
    }
}

Boolean doLogin()
{
    def success = false
    clearCookies()
    
    try
    {
        def postBody = [
            username: "${settings.username}",
            password: "${settings.password}"
            ]
    
        httpPost([
                uri: "$SERVER_ROOT/login",
                body: postBody
            ])
        { resp ->
            def responseStatus = resp.status
            logDebug "Status $responseStatus"
            
            gatherCookies(resp)
            success = (responseStatus >= 200 && responseStatus < 300)
        }
    }
    catch (HttpResponseException e)
    {
        if (e.getStatusCode() == 422)
        {
            log.error "Invalid credentials: $e"
        }
        else
        {
            log.error "Error on login: $e"
        }
        return false
    }
    catch (e)
    {
        log.error "Error on login: $e"
        return false
    }
    
    clearCredentialsIfNeeded()
    
    return success
}

def clearCredentialsIfNeeded()
{
    if (!settings.saveCredentials)
    {
        app.removeSetting("username")
        app.removeSetting("password")
    }
}

def getLocations()
{
    def success = false
    try
    {
        fireplaceLocations = [:]
        
        httpGet([
                uri: "$SERVER_ROOT/enumlocations",
                headers: [ 'Cookie': makeCookiesString() ]
            ])
        { resp ->
            logDebug "Status ${resp.status}  Location Data ${resp.data}"
            gatherCookies(resp)

            resp.data.locations.each { location -> fireplaceLocations[location.location_id] = location.location_name }            
            return true
        }
    }
    catch (e)
    {
        log.error "Error enumerating locations: $e"
        return false
    }
}

def getFireplaces(locationId)
{
        fireplacesInfo = [:]
        try
        {
            httpGet([
                uri: "$SERVER_ROOT/enumfireplaces?location_id=$locationId",
                headers: [ 'Cookie': makeCookiesString() ]
            ])
            { resp ->
                logDebug "Status ${resp.status}  Fireplace Data ${resp.data}"
                gatherCookies(resp)
        
                resp.data.fireplaces.each { fireplace ->
                    fireplacesInfo[fireplace.serial] = [ name: fireplace.name, apiKey: fireplace.apikey, serial: fireplace.serial ]
                }
            
                return true
            }
        }
        catch (e)
        {
            log.error "Error enumerating fireplaces: $e"
            return false
        }
}

def getFireplacesList()
{
    logDebug "fireplacesInfo: $fireplacesInfo"
    return fireplacesInfo.collectEntries { serial,data -> [serial, data.name] }
}

def getFireplaceInfos(fireplaceSerials)
{
    clearStatusMessage()
    fireplaceSerials.each { serial -> getFireplaceInfo(serial) }
}

def getFireplaceInfo(fireplaceSerial)
{
    def fireplace = fireplacesInfo[fireplaceSerial]

    try
    {
        def auth = getCookieValue("auth_cookie")
        
        httpGet([
            uri: "$SERVER_ROOT/$fireplaceSerial/$auth/apppoll",
            headers: [ 'Cookie': makeCookiesString() ]
        ])
        { resp ->
            logDebug "Status ${resp.status}  Fireplace Info ${resp.data}"
            gatherCookies(resp)
        
            if (!resp.data.containsKey("ipv4_address"))
            {
                addStatusMessage("Failed to get IP address for ${fireplace.name}.  Try resetting your fireplace module and make sure you can connect using the IntelliFire mobile app.")
                log.error "Failed to get IP address for ${fireplace.name}"
            }
            else
            {
                fireplace.ipAddress = resp.data.ipv4_address
                if (resp.data.containsKey("feature_thermostat"))
                {
                    logDebug "feature_thermostat ${resp.data.feature_thermostat}"
                }
                fireplace.hasThermostat = (resp.data.containsKey("feature_thermostat") && resp.data.feature_thermostat == "1")
                logDebug "fireplace.hasThermostat ${fireplace.hasThermostat}"
                
                createFireplace(fireplace)
            }
        }
    }
    catch (e)
    {
        addStatusMessage("Error receiving info for fireplace ${fireplace.name}: ${e}")
        log.error "Error receiving info for fireplace ${fireplace.name}: ${e}"
    }
}

def createFireplace(fireplace)
{
    def userId = getCookieValue("user")
    Boolean isCreatingFireplace = false
    
    logDebug "creating fireplace $fireplace"
    
    try
    {            
        def fireplaceDni = "IntelliFire-${fireplace.serial}"

        // If device exists
        def device = getChildDevice(fireplaceDni)
        if (device == null)
        {
            isCreatingFireplace = true
            device = addChildDevice("IntelliFire", "IntelliFire Fireplace", fireplaceDni, [label: fireplace.name])
        }

        // Apply settings to device
        device.updateSetting("ipAddress", fireplace.ipAddress)
        device.updateSetting("apiKey", fireplace.apiKey)
        device.updateSetting("userId", userId)
        device.updateSetting("thermostatOnDefault", fireplace.hasThermostat)
        device.configure()

        // Report result string
        def action = isCreatingFireplace ? "created" : "refreshed"
        addStatusMessage("Successfully $action fireplace ${fireplace.name}")
    }
    catch (e)
    {
        // Report error
        def action = isCreatingFireplace ? "create" : "refresh"
        addStatusMessage("Failed to $action fireplace ${fireplace.name}: $e")
        log.error "Failed to $action fireplace ${fireplace.name}: $e"
    }
}

void clearCookies()
{
    state.sessionCookies = [:]
}

void gatherCookies(response)
{
    response.getHeaders('Set-Cookie').each
    {
        String cookie = it.value.split(';')[0]
        def cookieParts = cookie.split('=')

        if (cookieParts[1].trim() != "")
        {
            //logDebug "Adding cookie to collection: \"${cookieParts[0]} = ${cookieParts[1]}\""
            state.sessionCookies[cookieParts[0]] = cookieParts[1]
        }
    }
}

String makeCookiesString()
{
    String cookiesString = ""

    state.sessionCookies.each { key, value -> cookiesString += key + '=' + value + ';' }

    //logDebug "cookiesString: $cookiesString"
    
    return cookiesString
}

String getCookieValue(key)
{
    return state.sessionCookies[key]
}
