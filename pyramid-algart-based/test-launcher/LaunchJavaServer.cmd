rem If you have no java.exe in Windows PATH, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

set CP=pp-config/lib/sdk/*;pp-config/lib/maven/algart/*;pp-config/lib/maven/json/*;pp-config/lib/maven/jai/*;pp-config/lib/maven/grizzly/*;pp-config/lib/maven/loci/*
start java -Xmx6g -classpath %CP% net.algart.pyramid.http.server.HttpPyramidServer --groupId=net.algart.simagis.pyramid.sources.imageio pp-config/services
start java -Xmx2g -classpath %CP% net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.loci pp-config/services
