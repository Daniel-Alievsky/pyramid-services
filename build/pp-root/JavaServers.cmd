@echo off
call FindJava
rem If this CMD-file cannot find java.exe, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

set CP=pyramid-services/lib/algart/*;pyramid-services/lib/json/*
%java% -classpath %CP% net.algart.pyramid.http.control.HttpPyramidServersLauncher %1 %2 %3 . host-conf-example/server-settings.json