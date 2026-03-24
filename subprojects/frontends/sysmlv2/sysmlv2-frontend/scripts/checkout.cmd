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
if not exist "%TARGET_DIR%\.git" (
    git clone "%REMOTE_URL%" "%TARGET_DIR%"
    if errorlevel 1 exit /b 1
)

cd /d "%TARGET_DIR%"
if errorlevel 1 exit /b 1

git fetch origin "%COMMIT%"
if errorlevel 1 exit /b 1

git checkout "%COMMIT%"
if errorlevel 1 exit /b 1

endlocal
