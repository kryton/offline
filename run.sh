export REPO=devsand00583.c034.digitalriverws.net:5000
docker run -v /pic/cache:/pic/cache -v /pic/source:/pic/source --name dr_offline -d  -p 9000:9000 ${REPO}/dr_offline
