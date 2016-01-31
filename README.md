dns-made-easy-updater
=====================
- Download: [See releases](https://github.com/andrewkroh/dns-made-easy-updater/releases)
- Author: Andrew Kroh
- License: Apache License, Version 2.0
- Supported OS: all supported by Go

What is it?
-----------
This is a command line utility for updating dynamic DNS records managed by DNS
Made Easy.

How does it work?
-----------------
The utility makes HTTPS calls to a RESTful service hosted by DNS Made Easy. See
http://oldcp.dnsmadeeasy.com/enterprisedns/ddnstechspec.html for information on
the communication specification.

The utility can be used behind NAT because it determines your public IP address
using a "reflector" hosted by DNS Made Easy. The utilities call out to the
"reflector" which simply echos back the IP address from which the request
originated. That address is then used in the subsequent DDNS update.

Proxy Users: If you need to use this utility behind a proxy server you can set
the  HTTP and HTTPS proxy servers using standard Go environment variables. See
https://golang.org/pkg/net/http/#ProxyFromEnvironment But be warned that the
proxies public IP address will be used in your DDNS updates.

Command Line Usage
------------------

```
Usage of ./dns-made-easy-updater:
  -config string
    	YAML config file containing username, password, record-id, and current-ip-file.
  -current-ip-file string
    	File containing the current public IP address. It will be created if it does not exist.
  -insecure
    	allow insecure SSL connections
  -interval string
    	Interval at which to check to IP address changes. If not given then one check (and possibly an update) is performed then it exits.
  -password string
    	Password for updating the DNS record.
  -record-id string
    	ID number of DDNS record.
  -username string
    	Username for updating the DNS record.
  -v	Enable verbose output to stderr
```

Building and Packaging
----------------------
You must have Go installed and have your GOPATH environment variable configured.

    go get github.com/andrewkroh/dns-made-easy-updater

That command will download, build, and install the binary into ``$GOPATH/bin`.
