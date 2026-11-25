@echo off
rem Build, copy to the T24 server, then restart T24
cd /d "%~dp0"
call mvn clean deploy %* || exit /b 1
call mvn antrun:run@restart-t24 %*
