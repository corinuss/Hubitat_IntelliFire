# Hubitat Intellifire Release Notes
This doc will contain a record of significant changes to this package.  Minor version updates might not be reported here.  Refer to the changelog at the top of the individual groovy files for those changes.

## 2.0.0

### Breaking Changes 
The Hubitat Intellifire interface has gotten a bit messy as features have been added.  Some commands are now redunant.  Some attributes shouldn't be attributes, or follow different naming conventions, etc.  So since breaking changes are allowed during major update, this update fixes some of these issues.  Except for a couple cases, users shouldn't be affected by these changes unless they are trying to be power users

#### Removed commands
Light commands have been removed from the main Fireplace driver to reduce button clutter.  You must use the Fireplace Light child device to control the Lights (and it's better supported by Hubitat).  If it doesn't exist, you can create it with the createVirtualLightDevice button.
* lightOn
* lightOff
* setLightLevel

This command has been renamed to better align the ThermostatMode capability (though that capability has not been added due to button clutter).  If you use Google Home Community to control this fireplace, update these settings.
* setOnOff -> Renamed to setThermostatMode and now supports parameters ["off", "heat"]

#### Removed attributes
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
* temperatureRaw
* timeremaining

### Cloud Control Support
You now have the option to control the fireplace via cloud commands if you store your credentials in the Intellifire Manager App.  There is a setting on the Fireplace to change between local and cloud control.

#### Soft Reset
New command that will attempt a reset of your module if it is having issues.  This can sometimes fix the fireplace without a manual power cycle.
Unlike the other commands, this command will ALWAYS run in the cloud (because it's not supported locally), even when cloud control is disable.  For this command to work, you MUST have your online credentials stored in the app.

#### Cloud vs Local - Which should I choose?
I currently recommend Cloud control for stability reasons.  But here's a detailed breakdown on why to choose each one.

Cloud control has the following advantages:
* Significantly more stable.  Local control has a tendency to put the fireplace in a bad state (ECM_OFFLINE), requiring users to manually power cycle the module or fireplace every couple weeks.
* Status updates are seen almost immediately due to long polling feature.  (Status updates during local control may be delayed a few minutes.)  This allows you to use the Intellifire remote or mobile app without the Hubitat app getting temporarlily out of sync.
* Static IP for the fireplace is not required.

Local control has the following advantages:
* Internet not required after the fireplace is initially set up.  All traffic stays local to the network.  If the cloud goes down, the fireplace control continues to work.
* No need to store online credentials in the app.
