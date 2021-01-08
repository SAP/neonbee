@echo off

setlocal

:: Set JAVA_HOME in order to use different java than the default one on the system
set JAVA_EXE="java"
if defined JAVA_HOME set JAVA_EXE="%JAVA_HOME%\bin\java"

%JAVA_EXE% -version
if errorlevel == 1 (
    @echo JAVA HOME is not set correctly.
    goto end
)

:: Currently only java 11 is supported
%JAVA_EXE% -version 2>&1 | findstr /I /R 11. > nul
if errorlevel == 1 (
    @echo JAVA version is not correct. The only supported version is 11. Please check your JAVA_HOME environment variable.
    goto end
)

:: Start NeonBee
:: Please note it is not possible to use -classpath/-cp option in conjunction with -jar
@echo NeonBee will now be started on port: 8080
%JAVA_EXE% -classpath libs/*;. io.neonbee.Launcher -working-directory .

endlocal

:end
pause
