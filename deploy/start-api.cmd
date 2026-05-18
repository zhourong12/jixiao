@echo off
setlocal

set "ROOT=%~dp0.."
set "JAR=%ROOT%\dist\server\server-0.0.1-SNAPSHOT.jar"
set "ENV_FILE=%ROOT%\deploy\.env"

if not exist "%JAR%" (
  echo 未找到 JAR：%JAR%
  exit /b 1
)

if exist "%ENV_FILE%" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" set "%%A=%%B"
  )
)

if "%SESSION_JWT_SECRET%"=="" (
  echo 请先在 %ENV_FILE% 配置 SESSION_JWT_SECRET
  exit /b 1
)

if "%JAVA_OPTS%"=="" set "JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

java %JAVA_OPTS% -jar "%JAR%"
