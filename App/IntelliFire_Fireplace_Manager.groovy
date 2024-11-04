/**
 *  IntelliFire Fireplace Manager
 *
 *  Created by Eric Will (corinuss)
 *
 *  Allows a user to create local Hubitat IntelliFire fireplace devices using
 *  information queried from the IntelliFire servers.
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
 *    11/03/2024 v2.2.0   - Fireplace name is now assigned to device name rather than label (to allow user override).
 *    05/05/2024 v2.1.0   - Cloud Polling can now be set independently from Control.
 *    01/15/2024 v2.0.0   - Cloud Control support and a lot of cleanup.  See Release Notes for details.
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
        page(name: "addCredentialsPage")
        page(name: "addCredentialsResultPage")
        page(name: "removeCredentialsPage")
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

    app.removeSetting("createLightDevices")
    
    dynamicPage(name: "mainPage", nextPage: "", uninstall: false, install: true)
    {
        section("")
        {
            href "loginPage", title: "Add Fireplace(s)", description: "Add a new fireplace or refresh an existing fireplace from online settings"
            href "addCredentialsPage", title: "Add/Update Credentials", description: "Add or update online credientials."
            href "removeCredentialsPage", title: "Remove Credentials", description: "Remove online credientials."

            input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
        }
    }
}

def addCredentialsPage()
{
    logDebug "addCredentialsPage"
    
    return dynamicPage(name: "addCredentialsPage", title: "Add/Update IntelliFire Credentials", nextPage: "addCredentialsResultPage") {
        section("Login Credentials")
        {
            paragraph "Add or Update your Intellifire login credentials."
            paragraph "This is required if you want to use cloud control, which is more reliable than local control."
            input(name: "username", type: "email", title: "Username", description: "IntelliFire Username (email address)")
            input(name: "password", type: "password", title: "Password", description: "IntelliFire Password")
        }
    }
}

def addCredentialsResultPage()
{
    logDebug "addCredentialsResultPage"
    settings.saveCredentials = true

    if (doLogin())
    {
        return dynamicPage(name: "addCredentialsSuccessPage", title: "Validated", nextPage: "mainPage") {
            section("")
            {
                paragraph "Login successful."
            }
        }
    }
    else
    {
        return dynamicPage(name: "addCredentialsErrorPage", title: "Login Error", nextPage: "addCredentialsPage") {
            section("")
            {
                paragraph "The username or password you entered is incorrect."
            }
        }
    }
}

def removeCredentialsPage()
{
    logDebug "removeCredentialsPage"

    settings.saveCredentials = false
    clearCredentialsIfNeeded()
    clearCookies()

    getChildDevices()?.each
    {
        it.notifyLoginChange(false, atomicState.loginUniqueId)
    }

    return dynamicPage(name: "removeCredentialsPage", title: "IntelliFire Credentials Removed", nextPage: "mainPage") {
        section("Login Credentials")
        {
            paragraph "Your Intellifire credentials have been deleted."
        }
    }
}

def loginPage()
{
    logDebug "loginPage"
    
    return dynamicPage(name: "loginPage", title: "Connect to IntelliFire Servers", nextPage: "loginResultPage") {
        section("")
        {
            paragraph "We need to obtain some information from IntelliFire's servers to allow us to gain local access to the fireplace.  Once the fireplace is set up, all future communication will be done locally, unless cloud control is enabled."
            paragraph "Before continuing, please ensure that you can access your fireplace(s) from the IntelliFire mobile app.  Also, if you intend to use local control, ensure that you've placed your fireplace(s) on a static IP.  (Use your router's DHCP to reserve an IP for the fireplace.)"
            paragraph "Please provide IntelliFire login credentials."
            input(name: "username", type: "email", title: "Username", description: "IntelliFire Username (email address)")
            input(name: "password", type: "password", title: "Password", description: "IntelliFire Password")
            input(name: "saveCredentials", type: "bool", title: "Save Credentials?", description: "Prevents the need to re-enter credentials if you need to manually refresh the fireplace.  Saved credentials are also required for cloud control.", required: true, defaultValue: true)
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
    app.removeSetting("location")
    
    return dynamicPage(name: "fireplacesPage", title: "Fireplaces", nextPage: "createResultsPage") {
        section("Choose fireplaces to add or refresh") {
            input(name: "fireplaces", title: "Fireplaces", type: "enum", required: true, multiple: true, options: getFireplacesList())
        }

        section("Local or Cloud Control?")
        {
            paragraph "Local control and polling do not require an internet connection once the fireplace is configured, but can sometimes lock up the wifi module, requiring a power cycle.  Cloud polling also reacts to status updates immediately, allowing the Hubitat device to stay more in sync with the mobile app and physical remote."
            paragraph "Cloud control and polling are recommended.  You can change this later on device settings."
            if (settings.saveCredentials)
            {
                input(name: "enableCloudControl", title: "Enable Cloud Control?", type: "bool", defaultValue: true)
                input(name: "enableCloudPolling", title: "Enable Cloud Polling?", type: "bool", defaultValue: true)
            }
            else
            {
                paragraph "Cloud control is disabled since credentials weren't saved."
                app.updateSetting("enableCloudControl", [type:"bool", value: false])
                app.updateSetting("enableCloudPolling", [type:"bool", value: false])
            }
        }
    }
}

def createResultsPage(params)
{
    logDebug "fireplaces: $fireplacesInfo"

    getFireplaceInfos(settings.fireplaces)
    app.removeSetting("fireplaces")

    return dynamicPage(name: "createResultsPage", title: "Results", nextPage: "mainPage") {
        section {
            paragraph getStatusMessage()
        }
    }
}

String getRemoteServerRoot()
{
    return SERVER_ROOT
}

Boolean doLogin(clearCredentialsIfInvalid = false)
{
    def success = false

    if (!settings.username)
    {
        log.error "Login aborted.  Credentials not provided."
        return false;
    }

    synchronized(this)
    {
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

                // Bump the id so everyone knows credentials have changed.
                atomicState.loginUniqueId = (atomicState.loginUniqueId ?: 0) + 1
                success = true
            }
        }
        catch (HttpResponseException e)
        {
            if (e.getStatusCode() == 422 || e.getStatusCode() == 403)
            {
                log.error "Invalid credentials: $e"

                if (clearCredentialsIfInvalid)
                {
                    settings.saveCredentials = false
                    clearCredentialsIfNeeded()
                }
            }
            else
            {
                log.error "Error on login: $e"
            }
            success = false
        }
        catch (e)
        {
            log.error "Error on login: $e"
            success = false
        }

        if (success)
        {
            clearCredentialsIfNeeded()
        }

        getChildDevices()?.each
        {
            it.notifyLoginChange(success, atomicState.loginUniqueId)
        }
    }

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

def refreshCredentials()
{
    // Credentials should be valid, so if they suddenly become invalid,
    // stop trying to reuse them.
    doLogin(true)
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
                fireplace.data = resp.data
                
                if (!fireplace.data.containsKey("serial"))
                {
                    // Data from the cloud interface doesn't contain the serial since the serial is required to query,
                    // so inject it into the data to match local behavior, so we can initialize it.
                    fireplace.data.serial = fireplaceSerial
                }

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
            device = addChildDevice("IntelliFire", "IntelliFire Fireplace", fireplaceDni, [name: fireplace.name])
        }

        // Apply settings to device
        def hasThermostat = (fireplace.data.containsKey("feature_thermostat") && fireplace.data.feature_thermostat == "1")

        //device.updateSetting("enableDebugLogging", true)
        device.updateSetting("ipAddress", fireplace.data.ipv4_address)
        device.updateSetting("apiKey", fireplace.apiKey)
        device.updateSetting("userId", userId)
        device.updateSetting("thermostatOnDefault", hasThermostat)
        device.updateSetting("enableCloudControl", settings.enableCloudControl)
        device.updateSetting("enableCloudPolling", settings.enableCloudPolling)
        device.consumePollData(fireplace.data)
        device.notifyLoginChange(settings.saveCredentials, getCurrentLoginId(), true)
        device.configure()

        if (fireplace.data.containsKey("feature_light") && fireplace.data.feature_light == "1")
        {
            device.createVirtualLightDevice()
        }

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
    atomicState.sessionCookies = [:]
}

void gatherCookies(response)
{
    response.getHeaders('Set-Cookie').each
    {
        String cookie = it.value.split(';')[0]
        def cookieParts = cookie.split('=')

        // Code saved in case we ever need to deal with expiring cookies.
        // Currently, they don't expire until end of session.

        // try {
        //     def cookieSegments = it.value.split(';')
        //     for (int i = 1; i < cookieSegments.length; i++) {
        //         def cookieSegment = cookieSegments[i]
        //         String cookieSegmentName = cookieSegment.split('=')[0]

        //         if (cookieSegmentName.trim() == "expires") {
        //             String expiration = cookieSegment.split('=')[1] 

        //             expires = new Date(expiration)
        //         } 
        //     }
        // }
        // catch (e) {
        //     log.info "!error when checking expiration date: $e ($expiration) {$it.value}"
        // }

        if (cookieParts[1].trim() != "")
        {
            //logDebug "Adding cookie to collection: \"${cookieParts[0]} = ${cookieParts[1]}\""
            atomicState.updateMapValue("sessionCookies", cookieParts[0], cookieParts[1])
        }
    }
}

def getCurrentLoginId()
{
    return atomicState.loginUniqueId ?: 0
}

String makeCookiesString(loginUniqueId = null)
{
    if (loginUniqueId != null && atomicState.loginUniqueId != loginUniqueId)
    {
        return null
    }

    String cookiesString = ""

    // Track expired cookies to remove, since we can't remove while iterating.
    // 06/19/2022 - Running a Map removeAll() call first to remove expired cookies would be cleaner, except that
    // it requires Groovy version 2.5.0, and Hubitat is currently on 2.4.21.  :(
    // def expiredCookies = []
    // def now = new Date();

    // atomicState.sessionCookies.each{ entry ->
    //     if (entry.value.containsKey(expiration) && entry.value.expiration < now)
    //     {
    //         expiredCookies << entry.key
    //     }
    //     else
    //     {
    //         cookiesString = cookiesString + entry.key + '=' + entry.value.value + ';'
    //     }
    // }

    // expiredCookies.each{ entry -> atomicState.sessionCookies.remove(entry) }
    
    atomicState.sessionCookies.each { key, value -> cookiesString += key + '=' + value + ';' }

    //logDebug "cookiesString: $cookiesString"
    
    return cookiesString
}

String getCookieValue(key)
{
    return atomicState.sessionCookies[key]
}
