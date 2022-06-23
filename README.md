# HubitatIntelliFire
 IntelliFire control for Hubitat

This module is based heavily on [intellifire4py](https://github.com/jeeftor/intellifire4py) by jeeftor for Home Assistant.

Except for initialization, the fireplace is controlled entirely via local http directly to the fireplace.  During setup, an apiKey unique to the fireplace (and some other ids) must be pulled from your online IntelliFire account to enable communication with the fireplace.

## Requirements
* Fireplace must have an IntelliFire WiFi module installed, and must already be registered and configured with an IntelliFire account via the IntelliFire app.
* Fireplace should have a static IP address.  (Use your router's DHCP setting to assign an IP to the fireplace.)

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
