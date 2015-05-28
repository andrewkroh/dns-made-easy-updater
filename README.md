dns-made-easy-updater
=====================
- Author: Andrew Kroh
- Website: http://blog.crowbird.com
- Download: http://blog.crowbird.com/artifacts/dns-made-easy-updater/
- License: Apache License, Version 2.0
- Supported OS: any that have a JVM
- Requirements: Java 1.5+

What is it?
-----------
The project creates two command line utilities for performing secure dynamic DNS updates 
with DNS Made Easy. One is the manualupdater which can be used for manually performing
an update by specifying all the parameters on the command line. The other utility is
autoupdater which reads the configuration parameters from a properties files, stores
the last known public IP address, and only performs an update when the IP address changes.

How does it work?
-----------------
The utilities make HTTPS calls to a RESTful service hosted by DNS Made Easy. 
See http://oldcp.dnsmadeeasy.com/enterprisedns/ddnstechspec.html for information 
on the communication specification.

The utilities can be used behind NAT because they determine your public IP address using
a "reflector" hosted by DNS Made Easy. The utilities call out to the "reflector" which
simply echos back the IP address from which your request originated. That address is
then used in the subsequent DDNS update.

Proxy Users: If you need to use this utility behind a proxy server you can set the 
HTTP and HTTPS proxy servers using standard Java command line properties. 
See http://download.oracle.com/javase/6/docs/technotes/guides/net/proxies.html for 
information on how to configure your proxies. But be warned that the proxies public
IP address will be used in your DDNS updates.

How do I use it?
----------------
1. Download the latest version from http://blog.crowbird.com/artifacts/dns-made-easy-updater.
2. Extract the file to your machine.
3. Modify config.properties to match the information for the DDNS record you want to update.
4. Execute `./autoupdater --config-file path/to/config.properties --current-ip-file where/to/save/current-ip.properties`
5. You should see "success" if all went well, otherwise you need to troubleshoot.
6. Once you have the command working you'll want to automate the execution using cron or Windows Scheduled Task.

For unix systems run crontab -e and add a line like this:
	
    * * * * * /home/user/dns-made-easy-updater/bin/autoupdater -c /home/user/dns-made-easy-updater/config.properties -i /home/user/dns-made-easy-updater/current-ip.properties >> /home/user/dns-made-easy-updater/update.log 2>&1

Command Line Usage
------------------
*****
    Usage: autoupdater [options]
      Options:
        -c, --config-file       Property file containing username, password, and
                                record-id.
                                Default: config.properties
        -i, --current-ip-file   Property file containing the current-ip property. It
                                will be created if it does not exist.
                                Default: current-ip.properties
        -d, --dont-update       No update will be performed. Used to create the
                                initial current-ip.properties file without doing an initial
                                update.
                                Default: false
*****
    Usage: manualupdater [options]
      Options:
      * -p, --password    Password for updating record
        -q, --quiet       No output during update
                          Default: false
      * -r, --record-id   ID number of DDNS record
      * -u, --username    Username for updating record

Building and Packaging
----------------------

    ./gradlew distZip distTar

This command will build the application and package it into a zip and tar.
The only requirement is that you have the JDK on your path. Gradle will automatically
be downloaded by the Gradle wrapper.
