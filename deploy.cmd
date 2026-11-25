@echo off
rem Build ALBECustomerLimit.jar and copy it to the T24 server (old JAR removed)
cd /d "%~dp0"
call mvn clean deploy %*
