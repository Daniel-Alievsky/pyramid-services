@echo Copying pp-images, pp-links, pp-build, server-configuration folders to the disk root
xcopy /S/Y pp-images\*.* \pp-images\
xcopy /S/Y pp-links\*.* \pp-links\
xcopy /S/Y pp-build\*.* \pp-build\
xcopy /S/Y server-configuration\*.* \server-configuration\
