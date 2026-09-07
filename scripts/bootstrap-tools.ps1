[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $projectRoot '.tools'
$nodeVersion = 'v22.23.2'
$nodeArchiveName = 'node-v22.23.2-win-x64.zip'
$nodeSha256 = '1177b4137ba5adaa56354ae40f1080c7450e8ae09cecb47da459d1c52ac99f97'
$nodeUrl = "https://nodejs.org/dist/$nodeVersion/$nodeArchiveName"
$nodeHome = Join-Path $toolsDir 'node-v22.23.2-win-x64'

$jdkArchiveName = 'OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip'
$jdkSha256 = '00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283'
$jdkUrl = 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip'
$jdkHome = Join-Path $toolsDir 'jdk-25.0.4.1+1'

function Install-VerifiedArchive {
    param(
        [Parameter(Mandatory)] [string] $Url,
        [Parameter(Mandatory)] [string] $ArchivePath,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)] [string] $InstalledPath
    )

    if (Test-Path $InstalledPath) {
        return
    }

    if (-not (Test-Path $ArchivePath)) {
        Invoke-WebRequest -Uri $Url -OutFile $ArchivePath
    }

    $actualSha256 = (Get-FileHash -Algorithm SHA256 $ArchivePath).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $ExpectedSha256) {
        throw "Checksum mismatch for $ArchivePath. Expected $ExpectedSha256, got $actualSha256."
    }

    Expand-Archive -Path $ArchivePath -DestinationPath $toolsDir -Force
    if (-not (Test-Path $InstalledPath)) {
        throw "Archive extracted but expected directory was not found: $InstalledPath"
    }
}

New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null

Install-VerifiedArchive `
    -Url $nodeUrl `
    -ArchivePath (Join-Path $toolsDir $nodeArchiveName) `
    -ExpectedSha256 $nodeSha256 `
    -InstalledPath $nodeHome

Install-VerifiedArchive `
    -Url $jdkUrl `
    -ArchivePath (Join-Path $toolsDir $jdkArchiveName) `
    -ExpectedSha256 $jdkSha256 `
    -InstalledPath $jdkHome

$node = Join-Path $nodeHome 'node.exe'
$npmCli = Join-Path $nodeHome 'node_modules\npm\bin\npm-cli.js'
$java = Join-Path $jdkHome 'bin\java.exe'

$env:PATH = "$nodeHome;$(Join-Path $jdkHome 'bin');$env:PATH"
$env:JAVA_HOME = $jdkHome

Push-Location $projectRoot
try {
    & $node $npmCli install --registry=https://registry.npmjs.org --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) {
        throw "npm install failed with exit code $LASTEXITCODE"
    }

    & $node $npmCli ls '@mcdxai/minecraft-dev-mcp' '@plugdev/mcp'
    if ($LASTEXITCODE -ne 0) {
        throw "MCP dependency verification failed with exit code $LASTEXITCODE"
    }

    & $java -version
    if ($LASTEXITCODE -ne 0) {
        throw "JDK verification failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host 'Project-local Node, JDK, and MCP dependencies are ready.'
