@echo off
call FindJava
rem If this CMD-file cannot find java.exe, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

set CP=pp-config/lib/sdk/*;pp-config/lib/maven/algart/*;pp-config/lib/maven/json/*;pp-config/lib/maven/jai/*;pp-config/lib/maven/grizzly/*;pp-config/lib/maven/loci/*
start "ImageIO pyramid service" %java% -Xmx6g -classpath %CP% net.algart.pyramid.http.server.HttpPyramidServer --groupId=net.algart.simagis.pyramid.sources.imageio pp-config/services pp-config/specific-server-settings-example.json
start "LOCI pyramid service" %java% -Xmx2g -classpath %CP% net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.loci pp-config/services pp-config/specific-server-settings-example.json
cd pp-config\services\bin\openslide
set CPOS=../../../lib/sdk/*;../../../lib/maven/algart/*;../../../lib/maven/json/*;../../../lib/maven/jai/*;../../../lib/maven/grizzly/*;../../../lib/maven/openslide/*
start "OpenSlide pyramid service" %java% -Xmx2g -classpath %CPOS% net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.openslide ../../ ../../specific-server-settings-example.json
cd ../../../../
start "Pyramid Proxy" %java% -classpath %CP% net.algart.pyramid.http.proxy.HttpPyramidProxyServer pp-config/services pp-config/specific-server-settings-example.json
