FROM        ubuntu:14.04

MAINTAINER  Ian Holsman <kryton@gmail.com>

ENV         ACTIVATOR_VERSION 1.2.10
ENV         DEBIAN_FRONTEND noninteractive

# INSTALL OS DEPENDENCIES
RUN         apt-get update; apt-get install -y software-properties-common unzip

# INSTALL JAVA 7
RUN         echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
            echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections && \
            add-apt-repository -y ppa:webupd8team/java && \
            apt-get update && \
            apt-get install -y  ca-certificates-java && \
            apt-get install -y oracle-java7-installer

# INSTALL TYPESAFE ACTIVATOR
RUN         cd /tmp && \
            wget http://downloads.typesafe.com/typesafe-activator/$ACTIVATOR_VERSION/typesafe-activator-$ACTIVATOR_VERSION.zip && \
            unzip typesafe-activator-$ACTIVATOR_VERSION.zip -d /usr/local && \
            mv /usr/local/activator-$ACTIVATOR_VERSION /usr/local/activator && \
            rm typesafe-activator-$ACTIVATOR_VERSION.zip


# COMMIT PROJECT FILES
RUN         mkdir -p /root/.m2/repository/org/webjars/bootswatch && \
            mkdir -p .m2/repository/org/webjars/bootswatch-parent
ADD         3.2.0-2-SNAPSHOT /root/.m2/repository/org/webjars/bootswatch/3.2.0-2-SNAPSHOT/
ADD         bootswatch-parent /root/.m2/repository/org/webjars/bootswatch-parent
ADD         app /root/app
ADD         lib /root/lib
ADD         test /root/test
ADD         conf /root/conf
RUN         cd /root/conf  && cp application.conf.docker application.conf
ADD         public /root/public
ADD         public/javascripts/hello.js /root/public/javascripts/hello.js
RUN         mkdir -p /root/public/stylesheets/
ADD         public/stylesheets/main.css /root/public/stylesheets/main.css
ADD         build.sbt /root/
ADD         project/plugins.sbt /root/project/
ADD         project/build.properties /root/project/
RUN         mkdir  -p /pic/cache  /pic/source && chown 777 /pic/cache /pic/source

# TEST AND BUILD THE PROJECT -- FAILURE WILL HALT IMAGE CREATION
#RUN         cd /root; /usr/local/activator/activator test stage
RUN         cd /root; /usr/local/activator/activator stage
RUN         cd  /root/conf; cp application.conf.docker application.conf
RUN         rm /root/target/universal/stage/bin/*.bat

# TESTS PASSED -- CONFIGURE IMAGE
WORKDIR     /root
ENTRYPOINT  target/universal/stage/bin/$(ls target/universal/stage/bin)
VOLUME      ["/pic/cache","/pic/source","/pic/logs"]
EXPOSE      9000
