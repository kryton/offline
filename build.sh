#!/usr/bin/env bash
export REPO=devsand00583.c034.digitalriverws.net:5000
docker build --rm=true -t ${REPO}/dr_offline .
