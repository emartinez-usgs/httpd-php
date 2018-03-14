Apache+PHP
============

An image for running Apache+PHP on CentOS.


Getting Started
---------------

The default build of this image presents a container that runs an HTTPD
(Apache) server with PHP support on CentOS.

### Image Layout

The server `DOCUMENT_ROOT` is set to `/var/www/html`, but the image is designed
to serve HazDev web applications. The web applications are located in
`/var/www/apps` and sub-directories and linked into Apache configuration
(see below). Implementing applications are reponsible for properly
placing web application content in the appropriate folder and generating and
linking the configuration correctly.

Logs are sent to stdout and may be streamed with `docker logs -f <CONTINER_ID>`.

Configuration files are stored in `/etc/httpd`. The main configuration file is
`/etc/httpd/conf/httpd.conf`. It sets up basic server parameters and includes
any `*.conf` files from the `/etc/httpd/conf.d` directory. These are included
from within each `server` directive in the main configuration file. An example
`webapp.conf` file is provided and may be modified/replaced to specify
redirects, rewrites, or other customizations as needed for the target
deployment.

### SSL

The image does not support SSL at this time.


Building
--------

The `Dockerfile` provided with this package provides everything that is
needed to build the image. The build system must have Docker installed in
order to build the image.

```
$ cd PROJECT_ROOT
$ docker build -t usgs/httpd-php .
```
> Note: PROJECT_ROOT should be replaced with the path to where you have
>       cloned this project on the build system


Running a Container
-------------------

The container host must have Docker installed in order to run the image as a
container. Then the image can be pulled and a container can be started directly.

```
$ docker run usgs/httpd-php
```

### Swtiches

Any standard Docker switches may be provided on the command line when running
a container. Some specific switches of interest are documented below.

#### Ports
```
-p HOST_PORT:CONTAINER_PORT
```
Within the container, the HTTPD server is configured to listen on port 80
and (for HTTP only).

#### Mounts
```
-v HOST_DIRECTORY:CONTAINER_DIRECTORY
```
Content may be copied directly into the running container using a
`docker cp ...` command, alternatively one may choose to simply expose a host
volume to the container for easier access.

### Examples

Run an Apache+PHP server in a container. Expose container port 80 as host
port 8080.

```
$ docker run \
  --name example-apache-php \
  -p 8080:80 \
  usgs/httpd-php:latest
```

At this point a user should be able to access the Apache server in a browser
from any system (assuming proper firewall configurations).
```
http://containerhost:8080/index.html
```


Debugging
---------

You may connect to a running container using the following command
```
$ docker exec -it --user root CONTAINER_NAME /bin/bash
```
> Note: CONTAINER_NAME should be the name provided to Docker when creating the
>       container initially. If not provided explicitly, Docker may have
>       assigned a random name. A container ID may also be used.

You may tail the container logs using the following commands
```
$ docker logs -f CONTAINER_NAME
```

