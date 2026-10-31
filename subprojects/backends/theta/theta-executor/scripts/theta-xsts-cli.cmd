@echo off

rem SPDX-FileCopyrightText: 2026 The Semantifyr Authors
rem
rem SPDX-License-Identifier: EPL-2.0

setlocal
set SCRIPTDIR=%~dp0
set PATH=%SCRIPTDIR%lib;%PATH%
set VERIFIER_NAME=Theta
if "%1"=="--version" (
    java "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" CEGAR --version
    exit /b %ERRORLEVEL%
)
if not defined THETA_JAVA_OPTS set THETA_JAVA_OPTS=-Xss512M -Xms1G -Xmx4G
java %THETA_JAVA_OPTS% "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" %*
