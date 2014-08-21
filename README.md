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
