#!/usr/bin/env bash
docker rm some-mysql
docker run --name some-mysql -p 3307:3306 -e MYSQL_ROOT_PASSWORD=mysecretpassword -d mysql
echo waiting 5 seconds for boot
sleep 5
mysql -u root -pmysecretpassword -P 3307 -h docker.local -e "create database offline;"
