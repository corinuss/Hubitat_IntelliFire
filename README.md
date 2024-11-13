# Hubitat IntelliFire
 IntelliFire control for Hubitat

This module was originally based on [intellifire4py](https://github.com/jeeftor/intellifire4py) by jeeftor for Home Assistant, but has been adapted to run natively on the Hubitat.

During initialization via the Intellifire Manager App, an apiKey unique to the fireplace (and some other ids) must be pulled from your online IntelliFire account to enable communication with the fireplace.  Afterwards, if local control is chosen, the fireplace is controlled entirely via local http directly to the fireplace.

## Requirements
* Fireplace must have an IntelliFire WiFi module installed.
* Fireplace must be registered and configured with an IntelliFire account via the IntelliFire mobile app.  (You should be able to control the fireplace from your mobile app.)
  * Google: https://play.google.com/store/apps/details?id=com.hearthandhome.intellifire.android
  * Apple: https://apps.apple.com/us/app/intellifire/id1456842149
* If using local control, Fireplace should have a static IP address.  (Use your router's DHCP setting to reserve an IP to the fireplace.)

## Installation
Using [Hubitat Package Manager](https://github.com/HubitatCommunity/hubitatpackagemanager) allows you to more easily install the driver and app, and will ensure that you are notified of updates when available.

If you don't want to use Hubitat Package Manager, you can also just manually copy the Driver and App code into your hub.

After installation, run the IntelliFire Fireplace Manager app that's now installed on your Hubitat.  Sign in using the same credentials as the IntelliFire mobile app, and the app will automatically pull the keys that it needs to manage your fireplace, and will find and create your fireplace device.  If local control is chosen, this is the only time communication leaves your network.

If updating from 1.x and are a power user, see the [Release Notes](https://github.com/corinuss/Hubitat_IntelliFire/blob/main/RELEASE_NOTES.md) for additional details and a list of breaking changes.

### Cloud vs Local - Which should I choose?
Cloud is (unusually) more responsive and more stable than Local.  But here's a detailed breakdown on why to choose each one.

Cloud
* \+ (Polling) Status updates are reported immediately (usually within 1 second) via long polling.  The status you see on Hubitat will always be up to date, regardless of what caused the change.
* \- All traffic must go through Intellifire's servers.  Credentials must be saved on the hub (though will only be used if the login session expires).

Local
* \+ No automation traffic leaves your local network (except for initially creating the fireplace device).  Will function even if the Intellifire servers are not available.
* \- Fireplace needs a static IP on your local network, since IP address is used to find the fireplace.
* \- (Polling) Polling can be delayed due to explicit refreshing.  The driver tries to update immediately after it does something that might change the status, but changes from other sources (mobile app or remote) may not be updated immediately.
* \- Unstable.  Often goes offline every few weeks in the winter and requires a physical reset (toggle the switch or power cycle the fireplace).  It's not yet known for certain by the home automation community what causes the local instability.

## Limitations

### Local control is unstable
The IntelliFire modules are notorious for being a bit unstable, where after some time (up to a few weeks) the module will stop responding to local commands and must be power cycled.  See the Troubleshooting section below for some tips on how to recover when you get into this state.

Cloud commands do not seem to cause this stability issue.  In addition, due to the way the polling works, status updates are much more immediate when Cloud control is used.  For this reason, Cloud control is strongly recommended.

**Update (Fall 2024)** - A Hearth & Home (Intellifire) dev reached out to me to let me know they have identified the issue causing the local instability and are working on a fix.  I do not have an ETA on the fix, but it will probably be deployed during a future firmware update cycle.  I will update when I know more and am able to.

### Thermostat Set Point not saved on fireplace
When thermostat controls are turned off, the thermostat set point is lost.  This means that your physical remote, the IntelliFire mobile app, and the Hubitat device driver have no way of sharing this value.

To work around this limitation, this device driver will attempt to cache the current set point whenever it is non-zero and will restore it when the thermostat is enabled.  But when using local polling, it might not catch every change by the mobile app or physical remote due to polling intervals.  It is highly recommended that you control the fireplace with only one of these systems, or use cloud polling to minimize the chance of changes being missed.

### All temperatures are based in Celsius
The IntelliFire module only understands whole Celsius temperatures.  If your Hubitat is set to Fahrenheit, they will be converted to and from Celsius.  This means your granularity is about 2°F.  Attempting to set a temperature in the middle of this range will automatically round down a degree in the setting.  For your best experience, adjust the temperature by at least 2°F each time.

### Hubitat Light limitation
It is impossible to have both Light and Switch capabilities on a device which control different features of the device due to Hubitat using the same interface for both capabilities.  If your fireplace has a light, a virtual light child device will be created to control the Light like any other light.  This will be created in the IntelliFire Fireplace Manager app during setup, or if deleted can be recreated later by pressing the **Create Virtual Light Device** button on the Fireplace.

## Capabilities list
The following Hubitat capabilities are supported and map to these Fireplace features.

### IntelliFire Fireplace
**FanControl** - Controls the fan.
**Refresh** - Forces an immediate refresh of fireplace state.
**Switch** - Turns the fireplace on and off.  By default will also autoamtically restore the thermostat setting.
**SwitchLevel** - Controls flame height.
**TemperatureMeasurement** - Reports the current room temperature as seen by the fireplace.
**ThermostatHeatingSetpoint** - Sets the thermostat temperature.
**ThermostatSetpoint** - Sets the thermostat temperature.  (Identical to ThermostatHeatingSetpoint)
**Tone** - Makes the fireplace beep.  (Many fireplaces won't actually beep on command though.)

### IntelliFire Fireplace Virtual Light
**Light** - Turns the light on/off.
**Refresh** - Forces an immediate refresh of light state.
**Switch** - Turns the light on/off. (Identical to Light)
**SwitchLevel** - Controls the light level (dimmer)

### IntelliFire Fireplace Virtual Fan
**FanControl** - Controls the fan.
**Refresh** - Forces an immediate refresh of fan state.
**Switch** - Turns the fan on/off. (Restores the fan to the previous setting when turning on.)
**SwitchLevel** - Allows the fan level to be set as a percentage.

## Troubleshooting
The IntelliFire modules are notorious for being a bit unstable.  They should be fine in most normal use cases, but overuse (hammering the fireplace with commands) or underuse (summer) can cause them to misbehave.  Here's some suggestions on how to fix it when it misbehaves.

* Try controlling the fireplace via the offical IntelliFire mobile app.  Does it work?  If not, there's something wrong with your fireplace that needs to be corrected.  I do not provide support for the fireplaces, but usually one or more of these steps resolves the issue.  Try each of these steps below one at a time to see if the issue is resolved.
  * Reset the WiFi module (there's a button on the module you can press) or toggle main power switch on the fireplace off and on to reboot the module.  The main power switch can often be found on the edge of your fireplace.
    * Alternatively, try a Soft Reset on the [iftapi.net](http://iftapi.net/webaccess/login.html) site.
      * If you have saved your credentials in the Intellifire Manager App, you can also hit the Soft Reset button on your Hubitat device.
    * Test using the mobile app.
  * Delete the fireplace from your IntelliFire account (using the mobile app) and try re-adding the Fireplace back onto your account.
    * If you don't see the "IntelliFire" access point during setup, you might need to cut the power entirely to your fireplace (via switch or circuit breaker) to complete the fireplace reset process before it can be added.
    * Test using the mobile app.  If the mobile app can control the fireplace, you will need to refresh Hubitat keys for the fireplace (below)

* If the mobile app can control the fireplace, try refreshing Hubitat's settings for the fireplace.
  * Launch the IntelliFire Fireplace Manager app that's included with this Hubitat package.
  * Sign in, select your fireplace, and complete the process as if you were adding the fireplace again.
  * Assuming you didn't delete your old fireplace device, all of your settings will be kept.  Only the various keys and IP address will be updated.

## Google Assistant (Google Home)
You can control this fireplace via your Google Home devices by using the Google Home Community app. Use the [Hubitat Package Manager](https://github.com/HubitatCommunity/hubitatpackagemanager) to install the "Google Home Community" app and follow [their instructions](https://github.com/mbudnek/google-home-hubitat-community/blob/master/README.md) on how to get set up.

### Device Configuration
The following lists the settings to change.  If a setting is not listed here, default is fine.

* Device type name: **Fireplace** (or whatever you want to name it)
* Device type: **Thermostat Heating Setpoint**
* Google Home device type:
  * If you have a thermostat on your fireplace, I recommend specifying **Thermostat** here so you can control the temperature via the GUI.
  * If you don't have a thermostat, you can specify **Fireplace** here for simple on/off controls.
* Device Traits  (create the following)
  * On/Off
    * All default settings are fine.
  * Temperature Setting (if you have a Thermostat)
    * Supported Modes: **Off, Heat**
      * Only these modes should be checked.
    * Other defaults are fine.
  * Brightness (only if you don't have a Thermostat, for controlling flame height)
    * All defaults are fine.
  * Fan Speed
    * (Optional) Supported Fan Speeds: **Low, Medium, Medium-High, High**
      * If you want to use names for fan speeds, only these four Hubitat speeds are supported.
      * Adjust "Google Home Level Names for" each fan speed if you want to use different terms.
    * Supports Fan Speed Percentage: **enabled**
    * Current Fan Speed Percentage Attribute: **fanspeedPercent**
    * Fan Speed Percent Command: **setSpeedPercentage**

Once you've configured a device, don't forget to tag your fireplace as this type so Google can see it.

### Sample Commands
*Hey Google...*
* *Turn on the fireplace.*
* *Set the fireplace temperature to 70 degrees.*
* *Set the fireplace fan to 50 percent.*
* *Set the fireplace fan to High.*
