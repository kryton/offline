offline
=======

An Employee directory app, which queries your MSFT's active directory servers to get it's info.

It currently queries AD for:

- employee information

- mailing list/security group/alias information that the person is subscribed to.

Some functional features:
-------------------------

- It filters results to filter out certain groups (eg terminated employees, 'resource' accounts)

- can scan multiple AD trees, and merges the results together. (as best as it can)

- looks for a 'photo' directory and displays the photo next to the person. It uses some face recognition software to get a better 'headshot'


Some non-functional features:
-----------------------------

- uses Docker to package this up in a container, to make it easier to run/install

- Uses Scala & Play


Configuration:
--------------

1. copy the 'xxxx.sh.dist' files in the home directory to their xxx.sh counterparts. the main thing to configure here is the location of your private repository that you will be pushing/pulling from.

2. locate your photos, and put them in either 'source' or 'cache' directory, and edit run.sh to point to them. 'source' files are cropped/adjusted, and the cached version is placed in 'cache'. 
There is a java file called util.BatchImageProcess that will do a bulk conversion for you. 

3. copy the conf/application.conf.dist to conf/application.conf and replace the 'example.com' machines with your own. The 'docker' configuration expects the image directories to be in /pic/ so you shouldn't need to modify them.

4. run './build.sh' and it should build your docker container than you can 'run.sh' .. 

5. alternatively 
$ sbt run


Finding the machines that are running active directory in your domain
---------------------------------------------------------------------
$ nslookup

>> set type=any

>>_ldap._tcp.dc._msdcs.XXXXXX.YYYYYYY.com

where XXXXX should be your Windows domain name that you log into. (eg REDMOND\johnsmith)
and YYYYY should be your domain name eg ( example.com)

or you could just ask your domain admin.. (but that's not as much fun).

