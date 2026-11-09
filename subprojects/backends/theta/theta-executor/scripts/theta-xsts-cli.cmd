@echo off

rem SPDX-FileCopyrightText: 2026 The Semantifyr Authors
rem
rem SPDX-License-Identifier: EPL-2.0

setlocal
set SCRIPTDIR=%~dp0
set PATH=%SCRIPTDIR%lib;%PATH%
set VERIFIER_NAME=Theta
set JAVA_EXE=java
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java"
if "%1"=="--version" (
    "%JAVA_EXE%" "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" CEGAR --version
    exit /b %ERRORLEVEL%
)
if not defined THETA_JAVA_OPTS set THETA_JAVA_OPTS=-Xss512M -Xms1G -Xmx4G
"%JAVA_EXE%" %THETA_JAVA_OPTS% "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" %*
