@echo off
setlocal
set SCRIPTDIR=%~dp0
set PATH=%SCRIPTDIR%lib;%PATH%
set VERIFIER_NAME=Theta
if "%1"=="--version" (
    java "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" CEGAR --version
    exit /b %ERRORLEVEL%
)
java -Xss512M -Xms1G -Xmx4G -XX:+UseG1GC -XX:+UseTransparentHugePages "-Djava.library.path=%SCRIPTDIR%lib" -jar "%SCRIPTDIR%jars\theta-xsts-cli.jar" %*
