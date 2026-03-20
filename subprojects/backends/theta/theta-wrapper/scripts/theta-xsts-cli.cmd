@echo off
setlocal
set SCRIPTDIR=%~dp0
set PATH=%SCRIPTDIR%lib;%PATH%
set VERIFIER_NAME=Theta
if "%1"=="--version" (
    java -Xss512M -Xmx4G "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" CEGAR --version
    exit /b %ERRORLEVEL%
)
java -Xss512M -Xmx4G "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" %*
