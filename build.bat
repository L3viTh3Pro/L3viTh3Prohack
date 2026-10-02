@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   ChunkScout - Fabric 1.21.11 Build
echo ========================================
echo.

REM Fabric Loom 1.14 requires Gradle 9.2 or newer.
set "GRADLE_VERSION=9.2.1"
set "GRADLE_DIR=%~dp0.gradle-local\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

where java >nul 2>nul
if errorlevel 1 (
  echo HIBA: Java nincs telepitve vagy nincs a PATH-ban.
  echo Java 21 szukseges a Minecraft 1.21.11 buildhez.
  echo.
  echo Telepits Java 21-et, majd inditsd ujra ezt a fajlt.
  pause
  exit /b 1
)

for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /C:"version"') do set "JAVA_VERSION=%%V"
echo Java: %JAVA_VERSION%
echo.

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Gradle nincs meg ezen a gepen. Letoltom automatikusan a Gradle %GRADLE_VERSION%-et...
  echo Forras: %GRADLE_URL%
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -UseBasicParsing -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ZIP%' } catch { Write-Host $_; exit 1 }"
  if errorlevel 1 (
    echo HIBA: Nem sikerult letolteni a Gradle-t.
    echo Ellenorizd az internetkapcsolatot, majd probald ujra.
    pause
    exit /b 1
  )
  if not exist "%GRADLE_ZIP%" (
    echo HIBA: A Gradle ZIP nem jott letre.
    pause
    exit /b 1
  )
  echo Gradle kicsomagolasa...
  if not exist "%~dp0.gradle-local" mkdir "%~dp0.gradle-local"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%~dp0.gradle-local' -Force } catch { Write-Host $_; exit 1 }"
  if errorlevel 1 (
    echo HIBA: Nem sikerult kicsomagolni a Gradle-t.
    pause
    exit /b 1
  )
  del /q "%GRADLE_ZIP%" >nul 2>nul
)

echo Gradle: %GRADLE_DIR%
echo.
echo Build indul... Ez eloszor sok idot vehet igenybe, mert a Fabric es Minecraft fajlokat is letolti.
echo.

call "%GRADLE_DIR%\bin\gradle.bat" build
set "ERR=%ERRORLEVEL%"

echo.
if not "%ERR%"=="0" (
  echo ========================================
  echo A build HIBA miatt nem sikerult.
  echo ========================================
  echo.
  echo Ha hibauzenetet latsz, kuldd el nekem a fekete ablak teljes szoveget.
  pause
  exit /b %ERR%
)

echo ========================================
echo SIKERES BUILD!
echo ========================================
echo.
echo A kesz mod fajljai:
echo %~dp0build\libs\
echo.
if exist "%~dp0build\libs\*.jar" (
  dir /b "%~dp0build\libs\*.jar"
) else (
  echo Nem talaltam JAR fajlt a build\libs mappaban.
)
echo.
pause
endlocal
