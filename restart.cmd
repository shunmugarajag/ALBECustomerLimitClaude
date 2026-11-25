@echo off
rem Restart T24 / JBoss on the server (deploy/restart_t24.sh) - affects everyone on DEV
cd /d "%~dp0"
call mvn antrun:run@restart-t24 %*
