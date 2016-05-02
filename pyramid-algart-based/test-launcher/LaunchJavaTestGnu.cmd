rem If you have no java.exe in Windows PATH, please replace "java" below by full path to your Java, for example:
rem "C:\Program Files\Java\jre1.8.0_77\bin\java.exe"
rem Note: you must use 64-bit Java, not 32-bit!

@echo Copying pp-images and pp-links folders to the disk root
xcopy /S/Y pp-images\*.* \pp-images\
xcopy /S/Y pp-links\*.* \pp-links\
set CP=pp-config/lib/api/*;pp-config/lib/maven/algart/*;pp-config/lib/maven/json/*;pp-config/lib/maven/jai/*;pp-config/lib/maven/grizzly/*;pp-config/lib/maven/*
java -Xmx8g -classpath %CP% net.algart.pyramid.standard.tests.OpenSourceImageFileAccessTest -gnu
