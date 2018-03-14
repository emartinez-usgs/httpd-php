ARG FROM_IMAGE='usgs/centos'
FROM $FROM_IMAGE

LABEL maintainer="Eric Martinez <emartinez@usgs.gov>" \
      dockerfile_version="1.0.0" \
      apache_version="2.4.x" \
      php_version="5.5.x"


# add webtatic for php55
COPY conf/webtatic.repo /etc/yum.repos.d/webtatic.repo

# install necessary packages
RUN yum install -y \
      autoconf \
      curl \
      cyrus-sasl-lib \
      gcc \
      g++ \
      httpd \
      libc-dev \
      libedit2 \
      libxml2 \
      make \
      php55w \
      php55w-gd \
      php55w-ldap \
      php55w-mysql \
      php55w-pdo \
      php55w-pecl-apcu \
      php55w-pgsql \
      pkg-config \
      which \
      xz-utils \
      && \
    yum clean all


# replace apache and php configuration
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY conf/httpd.conf /etc/httpd/conf/httpd.conf
COPY conf/php.ini /etc/php.ini

# copy some boiler plate files to document root
COPY html/ /var/www/html/


EXPOSE 80

WORKDIR /var/www/html
CMD [ "docker-entrypoint.sh" ]

