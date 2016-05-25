@echo off
set jre_8_64_found=false
FOR /D  %%G in ("%ProgramFiles%\Java\jre1.8*.*") DO (
    set jre_8_64_found=true
    set jre64=%%G
)
IF NOT %jre_8_64_found%==true GOTO default
IF NOT EXIST "%jre64%\bin\java.exe" GOTO default
set java="%jre64%\bin\java.exe"
echo Java-x64 found at %java%
goto end

:default
set java=java
echo Java-x64 JRE 1.8 NOT FOUND! Trying to use default %java%

:end