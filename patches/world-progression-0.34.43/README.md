# World Progression 0.34.43 hotfix

These are the two replacement source files for the 0.34.42 mod source bundle. They add an explicit no-intro reply for returning players and a bounded client timeout for servers that do not send a welcome response. The updated complete source archive and built JAR are attached to the `v0.34.43` GitHub release.

Build target: Minecraft 1.21.1, Fabric, Java 21. Both server and clients need the updated mod for the immediate reconnect reply; a new client also releases the cover automatically when an older server does not answer.
