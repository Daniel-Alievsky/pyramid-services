rem If you have no java.exe in Windows PATH, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

set CP=pp-config/lib/sdk/*;pp-config/lib/maven/json/*
java -classpath %CP% net.algart.pyramid.http.HttpPyramidServerLauncher %1 pp-config/services
