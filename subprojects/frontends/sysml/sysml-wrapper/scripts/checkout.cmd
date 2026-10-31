@echo off

rem SPDX-FileCopyrightText: 2026 The Semantifyr Authors
rem
rem SPDX-License-Identifier: EPL-2.0

setlocal

set TARGET_DIR=%1
set REMOTE_URL=%2
set COMMIT=%3

if "%TARGET_DIR%"=="" goto usage
if "%REMOTE_URL%"=="" goto usage
if "%COMMIT%"=="" goto usage
goto main

:usage
echo Usage: checkout.cmd ^<target-dir^> ^<remote-url^> ^<commit^>
exit /b 1

:main
cd /d "%TARGET_DIR%"

git init
rem the fork's scripts parse its sources and expect the committed LF endings
git config core.autocrlf false
echo Adding remote
git remote add origin "%REMOTE_URL%" 2> nul || rem
echo Fetch latest
git fetch
echo Checkout commit
git reset "%COMMIT%" --hard

endlocal
