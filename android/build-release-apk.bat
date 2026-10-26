@echo off
setlocal
cd /d "%~dp0"

rem Keep Java selection local to this script. Never fall back to an unsupported JDK.
set "GONC_JDK17="
call :try_jdk17 "%JAVA17_HOME%"
call :try_jdk17 "%JAVA_HOME%"
for %%R in ("%ProgramFiles%\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Amazon Corretto" "%USERPROFILE%\.jdks") do (
    for /d %%J in ("%%~R\*") do call :try_jdk17 "%%~J"
)
if not defined GONC_JDK17 (
    echo ERROR: JDK 17 was not found.
    echo Install JDK 17 or set JAVA17_HOME to its installation directory.
    echo Example: set "JAVA17_HOME=C:\Program Files\Java\jdk-17"
    timeout 5 >nul 2>nul || ping -n 6 127.0.0.1 >nul
    exit /b 1
)
set "JAVA_HOME=%GONC_JDK17%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JDK 17: %JAVA_HOME%

set "RELEASE_DIR=%~dp0app\build\outputs\apk\release"
set "SIGNED_APK=%RELEASE_DIR%\app-release.apk"
set "UNSIGNED_APK=%RELEASE_DIR%\app-release-unsigned.apk"

if exist "%SIGNED_APK%" del /f /q "%SIGNED_APK%"
if exist "%UNSIGNED_APK%" del /f /q "%UNSIGNED_APK%"

echo Building Gonc release APK...
call "%~dp0gradlew.bat" "-Dorg.gradle.java.home=%JAVA_HOME%" --no-daemon assembleRelease
if errorlevel 1 (
    echo.
    echo Release APK build failed.
    echo See the Gradle error above. For signing errors, check android\keystore.properties.
    timeout 5 >nul 2>nul || ping -n 6 127.0.0.1 >nul
    exit /b 1
)

if exist "%UNSIGNED_APK%" (
    echo.
    echo ERROR: unsigned release APK was produced, refusing to publish it:
    echo %UNSIGNED_APK%
    timeout 5 >nul 2>nul || ping -n 6 127.0.0.1 >nul
    exit /b 1
)

if not exist "%SIGNED_APK%" (
    echo.
    echo ERROR: signed release APK was not produced:
    echo %SIGNED_APK%
    timeout 5 >nul 2>nul || ping -n 6 127.0.0.1 >nul
    exit /b 1
)

echo.
echo Signed release APK:
echo %SIGNED_APK%
echo.
timeout 5 >nul 2>nul || ping -n 6 127.0.0.1 >nul
exit /b 0

:try_jdk17
if defined GONC_JDK17 exit /b 0
if "%~1"=="" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
if not exist "%~1\bin\javac.exe" exit /b 0
if not exist "%~1\release" exit /b 0
set "GONC_JDK_CANDIDATE_VERSION="
for /f "tokens=1,2 delims==" %%A in ('findstr /b "JAVA_VERSION=" "%~1\release"') do (
    if "%%A"=="JAVA_VERSION" set "GONC_JDK_CANDIDATE_VERSION=%%~B"
)
if "%GONC_JDK_CANDIDATE_VERSION%"=="17" set "GONC_JDK17=%~1"
if "%GONC_JDK_CANDIDATE_VERSION:~0,3%"=="17." set "GONC_JDK17=%~1"
exit /b 0
