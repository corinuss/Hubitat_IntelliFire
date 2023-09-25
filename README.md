# Hubitat IntelliFire
 IntelliFire control for Hubitat

This module is based heavily on [intellifire4py](https://github.com/jeeftor/intellifire4py) by jeeftor for Home Assistant.

Except for initialization via the app, the fireplace is controlled entirely via local http directly to the fireplace.  During setup, an apiKey unique to the fireplace (and some other ids) must be pulled from your online IntelliFire account to enable communication with the fireplace.

## Requirements
* Fireplace must have an IntelliFire WiFi module installed.
* Fireplace must be registered and configured with an IntelliFire account via the IntelliFire mobile app.  (You should be able to control the fireplace from your mobile app.)
  * Google: https://play.google.com/store/apps/details?id=com.hearthandhome.intellifire.android&pcampaignid=web_share
  * Apple: https://apps.apple.com/us/app/intellifire/id1456842149
* Fireplace should have a static IP address.  (Use your router's DHCP setting to reserve an IP to the fireplace.)

## Installation
Using [Hubitat Package Manager](https://github.com/HubitatCommunity/hubitatpackagemanager) allows you to more easily install the driver and app, and will ensure that you are notified of updates when available.

If you don't want to use Hubitat Package Manager, you can also just manually copy the Driver and App code into your hub.

## Limitations

### Thermostat Set Point not saved on fireplace
When thermostat controls are turned off, the thermostat set point is lost.  This means that your physical remote, the Intellifire mobile app, and the Hubitat device driver have no way of sharing this value.

To work around this limitation, this device driver will attempt to cache the current set point whenever it is non-zero and will restore it when the thermostat is enabled, but it might not catch every change by the mobile app or physical remote due to polling intervals.  It is highly recommended that you control the fireplace with only one of these systems, or expect to reset the thermostat each time.

### All temperatures are based in Celsius
The Intellifire module only understands whole Celsius temperatures.  If your Hubitat is set to Fahrenheit, they will be converted to and from Celsius.  This means your granularity is about 2°F.  Attempting to set a temperature in the middle of this range will automatically round down a degree in the setting.  For your best experience, also adjust the temperature by at least 2°F. 

### Light limitation
It is impossible to have both Light and Switch capabilities on a device which control different features of the device, due to Hubitat using the same interface for both capabilities.  If your fireplace has a light, you can control it by calling **setLightLevel**.  This can be done in the *Rule Machine* via a Custom Action.

## Troubleshooting
The Intellifire modules are notorious for being a bit unstable.  They should be fine in most normal use caes, but overuse (hammering the fireplace with commands) or underuse (summer) can cause them to misbehave.  Here's some suggestions on how to fix it when it misbehaves.

* Try controlling the fireplace via the offical Intellifire mobile app.  Does it work?  If not, there's something wrong with your fireplace that needs to be corrected.  I do not provide support for the fireplaces, but usually one or more of these steps resolves the issue.  Try each of these steps below one at a time to see if the issue is resolved.
  * Reset the WiFi module (there's a button on the module you can press) or toggle main power switch on the fireplace off and on to reboot the module.  The main power switch can often be found on the edge of your fireplace.
    * Alternatively, try a Soft Reset on the [iftapi.net](http://iftapi.net/webaccess/login.html) site.
    * Test using the mobile app.
  * Delete the fireplace from your Intellifire account (using the mobile app) and try re-adding the Fireplace back onto your account.
    * If you don't see the "Intellifire" access point during setup, you might need to cut the power entirely to your fireplace (via switch or circuit breaker) to complete the fireplace reset process before it can be added.
    * Test using the mobile app.  If the mobile app can control the fireplace, you will need to refresh Hubitat keys for the fireplace (below)

* If the mobile app can control the fireplace, try refreshing Hubitat's settings for the fireplace.
  * Launch the Intellifire Fireplace Manager app that's included with this Hubitat package.
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
    * Off Hubitat Mode: **off**
    * Heat Hubitat Mode: **on**
    * Set Mode Command: **setOnOff**
    * Current Mode Attribute: **switch**
  * Fan Speed
    * (Optional) Supported Fan Speeds: **Low, Medium, Medium-High, High**
      * If you want to use names for fan speeds, only these four Hubitat speeds are supported.
      * Adjust "Google Home Level Names for" each fan speed if you want to use different terms.
    * Supports Fan Speed Percentage: **enabled**
    * Current Fan Speed Percentage Attribute: **fanspeedpercent**
    * Fan Speed Percent Command: **setSpeedPercentage**

Once you've configured a device, don't forget to tag your fireplace as this type so Google can see it.

### Sample Commands
*Hey Google...*
* *Turn on the fireplace.*
* *Set the fireplace temperature to 70 degress.*
* *Set the fireplace fan to 50 percent.*
* *Set the fireplace fan to High.*
