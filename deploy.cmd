@echo off
rem Build ALBE_CustomerLimit.jar and copy it to the T24 server (old JAR removed)
cd /d "%~dp0"
call mvn clean deploy %*
