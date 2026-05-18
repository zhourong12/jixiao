$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Jar = Join-Path $Root "dist\server\server-0.0.1-SNAPSHOT.jar"
$EnvFile = Join-Path $Root "deploy\.env"

if (-not (Test-Path $Jar)) {
  throw "未找到 JAR：$Jar"
}

if (Test-Path $EnvFile) {
  Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -le 0) { return }
    $name = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    Set-Item -Path "Env:$name" -Value $value
  }
}

if (-not $env:SESSION_JWT_SECRET) {
  throw "请先在 $EnvFile 配置 SESSION_JWT_SECRET"
}

if (-not $env:JAVA_OPTS) {
  $env:JAVA_OPTS = "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
}

& java $env:JAVA_OPTS.Split(" ") -jar $Jar
