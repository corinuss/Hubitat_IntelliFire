# Hubitat Intellifire Release Notes
This doc will contain a record of significant changes to this package.  Minor version updates might not be reported here.  Refer to the changelog at the top of the individual groovy files for those changes.

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
