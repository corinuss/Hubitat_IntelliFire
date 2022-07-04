# HubitatIntelliFire
 IntelliFire control for Hubitat

This module is based heavily on [intellifire4py](https://github.com/jeeftor/intellifire4py) by jeeftor for Home Assistant.

Except for initialization, the fireplace is controlled entirely via local http directly to the fireplace.  During setup, an apiKey unique to the fireplace (and some other ids) must be pulled from your online IntelliFire account to enable communication with the fireplace.

## Current status as of version 0.4.0
* Fireplace driver can be manually added.
* All commands are available and are executed locally.
* Automatic polling is active.
  * 5 seconds after ever Hubitat command.
  * Every 5 minutes while the fireplace is on.  Every 15 minutes when fireplace is off.
    * This is to periodically synchronize with changes from the remote/webapp without hammering the fireplace too much.  These times may be tweaked later.
* Coming soon (before version 1.0)
  * App to automatically add fireplaces using IntelliFire credentails.

## Requirements
* Fireplace must have an IntelliFire WiFi module installed, and must already be registered and configured with an IntelliFire account via the IntelliFire app.
* Fireplace should have a static IP address.  (Use your router's DHCP setting to assign an IP to the fireplace.)

## Installation
Using [Hubitat Package Manager](https://github.com/HubitatCommunity/hubitatpackagemanager) allows you to more easily install the driver (and app when it's ready), and will ensure that you are notified of updates when available.  This package has not yet been added to the main list, so you'll need to manually point the manager to the online location of the package.

* Launch the Hubitat Package Manager app.
* Select "Install"
* Select "From a URL"
* Enter this URL for the package
  * https://raw.githubusercontent.com/corinuss/Hubitat_IntelliFire/main/packageManifest.json
* Click next to install Hubitat IntelliFire.

If you don't want to use Hubitat Package Manager, you can also just manually copy the Driver code into your hub.

## Manual driver setup
This method is NOT recommended.  A configuration app that automates this process more safely will be coming soon.

* Sign into the [HHT Web Interface](http://iftapi.net/webaccess/login.html) and obtain the following information:
  * Your user hash (aka user id)
  * Your fireplace's serial id
  * Your fireplace's apiKey
* On your hub, create a virtual device.
  * Set the type to IntelliFire Fireplace
  * Set the Device Network ID to "IntelliFire-\<serial_id\>" using the serial id you obtained from the website.
* After creating the device, set the IP Address, User Id, and ApiKey preferences with the information obtained from the website.

## Troubleshooting

### The fireplace is receiving commands, but they aren't having any effect.
Reset the wifi module (there's a button on the module you can press) or toggle main power switch on the fireplace off and on to reboot the module.  The main power switch can often be found on the edge of your fireplace.

Alternatively, try a Soft Reset on the iftapi.net site.