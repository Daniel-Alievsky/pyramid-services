@echo off
if %1.==. (
    echo Usage:
    echo     AddImage some-image [-gnu]
    echo If it is pyramid format, please add -gnu flag; .svs, .ndpi, .scn formats are recognized automatically.
    goto end
)
if not exist %1 (
    echo Image file %1 does not exist
    goto end
)
set CALLING_DIRECTORY=%~dp0
set SOURCE_FILE=%~f1
set RESULT_FILE_NAME=data%~x1

set GNU=FALSE
if %2.==-gnu. set GNU=TRUE
if %~x1.==.svs. set GNU=TRUE
if %~x1.==.SVS. set GNU=TRUE
if %~x1.==.ndpi. set GNU=TRUE
if %~x1.==.NDPI. set GNU=TRUE
if %~x1.==.scn. set gnu=TRUE
if %~x1.==.scn. set GNU=TRUE

set HTML_FILE=pyramids.html
if %GNU%==TRUE (
    echo GNU support required
    set FORMAT_NAME=loci
    set MY_FOLDER=\pp-images\gnu
    set PORT=9100
) else (
    echo Standard image format
    set FORMAT_NAME=imageIO
    set MY_FOLDER=\pp-images\standard
    set PORT=9001
)
call :rand
mkdir %MY_SUBFOLDER%
set PYRAMID_FOLDER=%MY_SUBFOLDER%
echo Copying %1 into %PYRAMID_FOLDER%\%RESULT_FILE_NAME%
copy %1 %PYRAMID_FOLDER%\%RESULT_FILE_NAME%
echo Creating %PYRAMID_FOLDER%\.pp.json
echo {>%PYRAMID_FOLDER%\.pp.json
echo     "fileName": "%RESULT_FILE_NAME%",>>%PYRAMID_FOLDER%\.pp.json
echo     "formatName": "%FORMAT_NAME%">>%PYRAMID_FOLDER%\.pp.json
echo }>>%PYRAMID_FOLDER%\.pp.json

set MY_FOLDER=\pp-links
call :rand
set LINK_FOLDER=%MY_SUBFOLDER%
set LINK_NAME=%MY_SUBFOLDER_NAME%
mkdir %LINK_FOLDER%
echo Creating %LINK_FOLDER%\config.json
echo {>%LINK_FOLDER%\config.json
echo     "pyramidPath": "%PYRAMID_FOLDER:\=/%",>>%LINK_FOLDER%\config.json
echo     "renderer": {>>%LINK_FOLDER%\config.json
echo        "format": "jpeg">>%LINK_FOLDER%\config.json
echo     }>>%LINK_FOLDER%\config.json
echo }>>%LINK_FOLDER%\config.json

echo Adding HTML link to %CALLING_DIRECTORY%%HTML_FILE%
echo ^<a href="html/PlanePyramidServiceTest.html?%LINK_NAME%:%PORT%:32"^>New test %LINK_NAME%: pyramid at %PYRAMID_FOLDER%, source file %SOURCE_FILE%^</a^>^<br^> >>%CALLING_DIRECTORY%%HTML_FILE%
goto :end

:rand
set MY_SUBFOLDER_NAME=ai%RANDOM%
set MY_SUBFOLDER=%MY_FOLDER%\%MY_SUBFOLDER_NAME%
if exist %MY_SUBFOLDER%\nul goto rand
goto end

:end
