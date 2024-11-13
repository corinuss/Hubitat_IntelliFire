# Hubitat Intellifire Release Notes
This doc will contain a record of significant changes to this package.  Minor version updates might not be reported here.  Refer to the changelog at the top of the individual groovy files for those changes.

## 2.3.0
### Child device cleanup

#### New Fan virtual Child Device (Optional)
Similar to the Light child device, you can now create a Fan child device for controlling only the Fan component.  This child device is optional, as you can still fully control the fan via the main fireplace device.

Some other Hubitat apps expect to be able to use the SwitchLevel capability to control fan percentage, though this is not part of the Hubitat specs.  The main fireplace module uses SwitchLevel to control flame height.  So if you need SwitchLevel for Fan control, this is the device to use.

#### Other Child Device Cleanup
* The Light Virtual Child Device now has its Device Name set (and updated) similarly to the main Fireplace.  This was missed during the 2.2.0 update.
* "createVirtualLightDevice" has been replaced with a more generic "createVirtualChildDevice" that can create any supported child device type (currently Light and Fan).

### Upgrade Firmware
You can now tell your fireplace to check for a firmware upgrade and upgrade if avaiable.

Also, scaffolding has been put in place to allow for this device driver to automatically request a firmware upgrade if your firmware is too old.  This feature is currently disabled, but will be used later to make a single upgrade request once H&H has released the new firmware to fix local control stability.

## 2.2.1
Supporting new firmware string variable name, which changed in firmware 3.12.0 (or 3.11.0).

## 2.2.0
### Device Name is now set from Intellifire data
Previously, when setting up your fireplace the first time, the fireplace name was assigned to the Device Label while the Device Name was left to the default value ("Intellifire Fireplace").  With this update, the fireplace name is now assigned to the Device Name, and that Device Name will be updated if you change the fireplace name in Intellifire software.  The Device Label will not be touched.

If updating from a previous revision, clear out the Device Label to sync your device name to Intellifire's name.  Going forward, you can use the Device Label to override the fireplace name in Hubitat.

### New Preference: Local retries while in Cloud mode
When Cloud mode is enabled, commands that fail due to network issues can now be automatically retried via a local command.

Note: Status updates will still only arrive over the cloud, so if you are unable to communicate with Intellifire's servers, the Hubitat will not get status updates after the command.  But at least you'll still have direct control of your fireplace in this situation.  Local polling is not attempted to minimize the risk of destabilizing the module.

### Other updates
* Various hardware details added and moved to the Device Details section of the driver since they rarely change.
* Failed 'Off' commands are retried every minute for up to 15 minutes.  This is being added as a safety feature if the command fails for any reason (such as network instability).  Turning the fireplace back on via Hubitat will cancel these retries.

## 2.1.0
* Cloud Polling can now be set independently from Control.
* New 'timerExpires' attribute to know when the current timer expires.  (ISO 8601 format)
* Fan Control now supports "on" speed.  (Restores previous speed value.)
* Fixed errors from spamming logs on every status update.
* Event descriptions updated to describe what happened.  (Hubitat standard.)

## 2.0.0

### If upgrading from 1.x...
If you intend to use cloud control, launch the Intellifire Manager App and have the app confirm your login credentials (even if you've previously saved them) to allow the devices to access the cloud.

Your thermostat setpoint, fan, and light values might have returned to defaults after upgrading.  This will only happen during your initial upgrade to 2.x.

#### Breaking Changes 
The Hubitat Intellifire interface has gotten a bit messy as features have been added.  Some commands are now redunant.  Some attributes shouldn't be attributes, or follow different naming conventions, etc.  So since breaking changes are allowed during major update, this update fixes some of these issues.  Except for a couple cases, users shouldn't be affected by these changes unless they are trying to be power users

##### Removed commands
Light commands have been removed from the main Fireplace driver to reduce button clutter.  You must use the Fireplace Light child device to control the Lights (and it's better supported by Hubitat).  If it doesn't exist, you can create it with the createVirtualLightDevice button.
* lightOn
* lightOff
* setLightLevel

This command has been renamed to better align the ThermostatMode capability (though that capability has not been added due to button clutter).  If you use Google Home Community to control this fireplace, update these settings.
* setOnOff -> Renamed to setThermostatMode and now supports parameters ["off", "heat"]

##### Removed attributes
Attributes have been removed to reduce state clutter

These have been renamed.  Update any rules that use these values.
* fanspeedpercent -> fanspeedPercent

These have been moved to internal state tracking.
* fanspeedLast
* feature_light
* lightLast
* serial
* setpoint
* setpointLast

These have been removed completely due to not being useful.
* fanspeed (functional duplicate of 'speed')
* temperatureRaw
* timeremaining

### Cloud Control Support
You now have the option to control the fireplace via cloud commands if you store your credentials in the Intellifire Manager App.  There is a setting on the Fireplace to change between local and cloud control.

#### Soft Reset
New command that will attempt a reset of your module if it is having issues.  This can sometimes fix the fireplace without a manual power cycle.
Unlike the other commands, this command will ALWAYS run in the cloud (because it's not supported locally), even when cloud control is disable.  For this command to work, you MUST have your online credentials stored in the app.

#### Cloud vs Local - Which should I choose?
Cloud is (unusually) more responsive and more stable than Local.  But here's a detailed breakdown on why to choose each one.

Cloud
* \+ Status updates are reported immediately (usually within 1 second) via long polling.  The status you see on Hubitat will always be up to date, regardless of what caused the change.
* \- All traffic must go through Intellifire's servers.  Credentials must be saved on the hub (though will only be used if the login session expires).

Local
* \+ No automation traffic leaves your local network (except for initially creating the fireplace device).  Will function even if the Intellifire servers are not available.
* \- Fireplace needs a static IP on your local network, since IP address is used to find the fireplace.
* \- Polling can be delayed due to explicit refreshing.  The driver tries to update immediately after it does something that might change the status, but changes from other sources (mobile app or remote) may not be updated immediately.
* \- Unstable.  Often goes offline every few weeks in the winter and requires a physical reset (toggle the switch or power cycle the fireplace).  It's not yet known for certain by the home automation community what causes the local instability.
