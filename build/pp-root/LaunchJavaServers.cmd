@echo off
call FindJava
rem If this CMD-file cannot find java.exe, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

set CP=pyramid-services/lib/algart/*;pyramid-services/lib/json/*;pyramid-services/lib/grizzly/*
start "ImageIO pyramid service" %java% -Xmx6g -classpath %CP%;pyramid-services/lib/.image-io/* net.algart.pyramid.http.server.HttpPyramidServer --groupId=net.algart.simagis.pyramid.sources.imageio . host-conf-example/specific-server-settings.json
start "LOCI pyramid service" %java% -Xmx2g -classpath %CP%;pyramid-services/lib/.loci/* net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.loci . host-conf-example/specific-server-settings.json
pushd pyramid-services\bin\.openslide
set CPOS=../../lib/algart/*;../../lib/json/*;../../lib/grizzly/*;../../lib/.openslide/*
start "OpenSlide pyramid service" %java% -Xmx2g -classpath %CPOS% net.algart.pyramid.http.server.HttpPyramidServer --groupId=com.simagis.pyramid.openslide ../../../ ../../../host-conf-example/specific-server-settings.json
popd
start "Pyramid Proxy" %java% -classpath %CP% net.algart.pyramid.http.proxy.HttpPyramidProxyServer . host-conf-example/specific-server-settings.json
