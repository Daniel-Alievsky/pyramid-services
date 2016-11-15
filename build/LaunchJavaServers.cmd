@echo off
call FindJava
rem If this CMD-file cannot find java.exe, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

set CP=pp-build/lib/algart/*;pp-build/lib/json/*;pp-build/lib/grizzly/*
start "ImageIO pyramid service" %java% -Xmx6g -classpath %CP%;pp-build/lib/.image-io/* net.algart.pyramid.http.server.HttpPyramidServer --groupId=net.algart.simagis.pyramid.sources.imageio pp-build/configuration server-configuration/specific-server-settings.json
start "LOCI pyramid service" %java% -Xmx2g -classpath %CP%;pp-build/lib/.loci/* net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.loci pp-build/configuration server-configuration/specific-server-settings.json
pushd pp-build\bin\.openslide
set CPOS=../../lib/algart/*;../../lib/json/*;../../lib/grizzly/*;../../lib/.openslide/*
start "OpenSlide pyramid service" %java% -Xmx2g -classpath %CPOS% net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.openslide ../../../pp-build/configuration ../../../server-configuration/specific-server-settings.json
popd
start "Pyramid Proxy" %java% -classpath %CP% net.algart.pyramid.http.proxy.HttpPyramidProxyServer pp-build/configuration server-configuration/specific-server-settings.json
