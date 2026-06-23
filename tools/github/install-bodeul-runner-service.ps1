param(
  [string] $RepoUrl = "https://github.com/bodeul110/Bodeul",
  [string] $RepoApi = "repos/bodeul110/Bodeul",
  [string] $SourceRunnerRoot = "D:\actions-runner\bodeul-preflight",
  [string] $ServiceRunnerRoot = "D:\actions-runner\bodeul-preflight-service",
  [string] $RunnerName = "$env:COMPUTERNAME-bodeul-preflight-service",
  [string] $AndroidSdk = (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
  [string] $ServiceAccount = "NT AUTHORITY\NETWORK SERVICE",
  [switch] $KeepStartupFallback
)

$ErrorActionPreference = "Stop"

$LogPath = Join-Path $PSScriptRoot "runner-service-install.log"
$StartupShortcut = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup\BoDeul GitHub Actions Runner.vbs"
$StartupScript = Join-Path $SourceRunnerRoot "start-bodeul-runner.ps1"

function Write-Log {
  param([string] $Message)

  $line = "$(Get-Date -Format "yyyy-MM-dd HH:mm:ss") $Message"
  Add-Content -LiteralPath $LogPath -Encoding UTF8 -Value $line
  Write-Host $line
}

function Assert-Admin {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = [Security.Principal.WindowsPrincipal]::new($identity)
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Admin PowerShell is required. Current user: $($identity.Name)"
  }
  Write-Log "Admin confirmed: $($identity.Name)"
}

function Set-RunnerEnvironment {
  $entries = @(
    "$env:SystemRoot\System32",
    "$env:SystemRoot",
    "$env:SystemRoot\System32\WindowsPowerShell\v1.0",
    "C:\Program Files\Git\bin",
    "C:\Program Files\Git\cmd",
    "C:\Program Files\Git\usr\bin",
    "C:\Program Files\nodejs",
    "C:\Program Files\GitHub CLI"
  )

  $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
  $machineEntries = @()
  if ($machinePath) {
    $machineEntries = $machinePath.Split(";") | Where-Object { $_ -ne "" }
  }

  foreach ($entry in $entries) {
    if (-not (Test-Path -LiteralPath $entry)) {
      continue
    }
    $exists = $machineEntries | Where-Object { $_.TrimEnd("\") -ieq $entry.TrimEnd("\") }
    if (-not $exists) {
      $machineEntries = @($entry) + $machineEntries
      Write-Log "Machine PATH added: $entry"
    }
  }

  [Environment]::SetEnvironmentVariable("Path", ($machineEntries -join ";"), "Machine")
  [Environment]::SetEnvironmentVariable("ANDROID_HOME", $AndroidSdk, "Machine")
  [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $AndroidSdk, "Machine")

  $env:PATH = (($entries + $machineEntries) | Select-Object -Unique) -join ";"
  $env:ANDROID_HOME = $AndroidSdk
  $env:ANDROID_SDK_ROOT = $AndroidSdk

  Write-Log "Service environment configured"
}

function Set-ServicePowerShellPolicy {
  $currentPolicy = "Unknown"
  $allowedPolicies = @("RemoteSigned", "Unrestricted", "Bypass")

  try {
    $currentPolicy = (Get-ExecutionPolicy -Scope LocalMachine).ToString()
  } catch {
    Write-Log "Could not read LocalMachine PowerShell execution policy: $($_.Exception.Message)"
  }

  if ($allowedPolicies -contains $currentPolicy) {
    Write-Log "LocalMachine PowerShell execution policy already allows local scripts: $currentPolicy"
    return
  }

  try {
    Set-ExecutionPolicy -Scope LocalMachine -ExecutionPolicy RemoteSigned -Force
  } catch {
    $afterPolicy = (Get-ExecutionPolicy -Scope LocalMachine).ToString()
    if ($allowedPolicies -notcontains $afterPolicy) {
      throw
    }
    Write-Log "Set-ExecutionPolicy returned an error, but effective policy is now: $afterPolicy"
    return
  }

  Write-Log "LocalMachine PowerShell execution policy set to RemoteSigned. Previous: $currentPolicy"
}

function Invoke-Step {
  param(
    [string] $Name,
    [scriptblock] $Body
  )

  Write-Log "Start: $Name"
  & $Body
  if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
    throw "$Name failed. exitCode=$LASTEXITCODE"
  }
  Write-Log "Done: $Name"
}

function New-ServiceRunnerRoot {
  if (-not (Test-Path -LiteralPath $SourceRunnerRoot)) {
    throw "Source runner path does not exist: $SourceRunnerRoot"
  }
  if (-not (Test-Path -LiteralPath $AndroidSdk)) {
    throw "Android SDK path does not exist: $AndroidSdk"
  }
  if ($ServiceRunnerRoot -ieq $SourceRunnerRoot) {
    throw "Service runner path must differ from source runner path."
  }
  if (-not ($ServiceRunnerRoot -like "D:\actions-runner\*")) {
    throw "Unexpected service runner path: $ServiceRunnerRoot"
  }

  if (Test-Path -LiteralPath $ServiceRunnerRoot) {
    $existingServices = @(Get-Service "actions.runner.*" -ErrorAction SilentlyContinue | Where-Object {
      $_.Name -like "*$RunnerName*" -or $_.DisplayName -like "*$RunnerName*"
    })
    if ($existingServices.Count -gt 0) {
      Write-Log "Existing service found; skipping runner file copy: $($existingServices[0].Name)"
      return
    }

    Remove-Item -LiteralPath $ServiceRunnerRoot -Recurse -Force
    Write-Log "Removed previous temporary service runner path: $ServiceRunnerRoot"
  }

  New-Item -ItemType Directory -Path $ServiceRunnerRoot -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "bin") -Destination $ServiceRunnerRoot -Recurse
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "externals") -Destination $ServiceRunnerRoot -Recurse
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "config.cmd") -Destination $ServiceRunnerRoot
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "run.cmd") -Destination $ServiceRunnerRoot
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "run-helper.cmd") -Destination $ServiceRunnerRoot
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "run-helper.cmd.template") -Destination $ServiceRunnerRoot
  Copy-Item -LiteralPath (Join-Path $SourceRunnerRoot "run-helper.sh.template") -Destination $ServiceRunnerRoot

  Write-Log "Service runner files copied: $ServiceRunnerRoot"
}

function Grant-ServiceRunnerAcl {
  $icacls = Join-Path $env:SystemRoot "System32\icacls.exe"
  if (-not (Test-Path -LiteralPath $icacls)) {
    throw "icacls.exe was not found: $icacls"
  }

  Invoke-Step "grant service runner folder acl" {
    & $icacls $ServiceRunnerRoot /grant "${ServiceAccount}:(OI)(CI)M" /T /C
  }
}

function Get-ServiceAclIdentity {
  if ($ServiceAccount -ieq "NT AUTHORITY\NETWORK SERVICE") {
    return "*S-1-5-20"
  }

  return $ServiceAccount
}

function Grant-AndroidSdkAcl {
  $icacls = Join-Path $env:SystemRoot "System32\icacls.exe"
  if (-not (Test-Path -LiteralPath $icacls)) {
    throw "icacls.exe was not found: $icacls"
  }

  $aclIdentity = Get-ServiceAclIdentity
  $androidRoot = Split-Path -Parent $AndroidSdk
  $localAppDataRoot = Split-Path -Parent $androidRoot
  $appDataRoot = Split-Path -Parent $localAppDataRoot

  Invoke-Step "grant Android SDK parent traverse acl" {
    foreach ($path in @($appDataRoot, $localAppDataRoot, $androidRoot)) {
      if (Test-Path -LiteralPath $path) {
        & $icacls $path /grant "${aclIdentity}:RX"
      }
    }
  }

  Invoke-Step "grant Android SDK read acl" {
    & $icacls $AndroidSdk /grant "${aclIdentity}:(OI)(CI)RX" /T /C
  }
}

function Get-RunnerService {
  @(Get-Service "actions.runner.*" -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -like "*$RunnerName*" -or $_.DisplayName -like "*$RunnerName*"
  })
}

function Register-ServiceRunner {
  $existingServices = @(Get-RunnerService)
  if ($existingServices.Count -gt 0) {
    foreach ($service in $existingServices) {
      if ($service.Status -eq "Running") {
        Restart-Service -Name $service.Name -Force
        Write-Log "Existing runner service restarted to refresh environment: $($service.Name)"
      } else {
        Start-Service -Name $service.Name
        Write-Log "Existing runner service started: $($service.Name)"
      }
    }
    return
  }

  Set-Location $ServiceRunnerRoot

  $registrationToken = gh api -X POST "$RepoApi/actions/runners/registration-token" --jq .token
  if (-not $registrationToken) {
    throw "Failed to fetch runner registration token."
  }

  Invoke-Step "configure runner service" {
    .\config.cmd --unattended --url $RepoUrl --token $registrationToken --name $RunnerName --labels bodeul,preflight --work _work --replace --runasservice --windowslogonaccount $ServiceAccount
  }

  $services = @(Get-RunnerService)
  if ($services.Count -eq 0) {
    throw "Runner service was not created."
  }

  foreach ($service in $services) {
    if ($service.Status -ne "Running") {
      Start-Service -Name $service.Name
    }
    Write-Log "Runner service start requested: $($service.Name)"
  }
}

function Disable-StartupFallback {
  if ($KeepStartupFallback) {
    Write-Log "Keeping Startup fallback by request."
    return
  }

  if (Test-Path -LiteralPath $StartupShortcut) {
    Remove-Item -LiteralPath $StartupShortcut -Force
    Write-Log "Startup fallback removed: $StartupShortcut"
  }

  $listenerIds = @(Get-Process -Name Runner.Listener,Runner.Worker -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
  $runnerProcesses = @(Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq "powershell.exe" -and $_.CommandLine -like "*start-bodeul-runner.ps1*") -or
    ($_.Name -eq "cmd.exe" -and $_.CommandLine -like "*D:\actions-runner\bodeul-preflight\run.cmd*")
  })

  $ids = @()
  $ids += $listenerIds
  $ids += @($runnerProcesses | Select-Object -ExpandProperty ProcessId)
  $ids += @($runnerProcesses | Select-Object -ExpandProperty ParentProcessId)
  $ids = $ids | Where-Object { $_ } | Select-Object -Unique

  foreach ($id in $ids) {
    Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    Write-Log "Stopped existing user runner process: $id"
  }
}

function Restore-StartupFallback {
  if (-not (Test-Path -LiteralPath $StartupScript)) {
    Write-Log "Startup fallback script does not exist; skipping restore: $StartupScript"
    return
  }

  $startupDir = Split-Path -Parent $StartupShortcut
  New-Item -ItemType Directory -Path $startupDir -Force | Out-Null
  $escapedScript = $StartupScript.Replace("""", """""")
  $vbs = @(
    'Set shell = CreateObject("WScript.Shell")',
    "shell.Run `"powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"`"$escapedScript`"`"`", 0, False"
  )
  Set-Content -LiteralPath $StartupShortcut -Encoding ASCII -Value $vbs
  Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $StartupScript) -WindowStyle Hidden
  Write-Log "Startup fallback restored and user runner start requested"
}

function Wait-RunnerService {
  $deadline = (Get-Date).AddSeconds(60)
  do {
    $services = @(Get-RunnerService)
    foreach ($service in $services) {
      $fresh = Get-Service -Name $service.Name
      Write-Log "Runner service status: $($fresh.Name) / $($fresh.Status)"
      if ($fresh.Status -eq "Running") {
        return
      }
    }
    Start-Sleep -Seconds 5
  } while ((Get-Date) -lt $deadline)

  throw "Runner service did not reach Running status within 60 seconds."
}

Set-Content -LiteralPath $LogPath -Encoding UTF8 -Value "$(Get-Date -Format "yyyy-MM-dd HH:mm:ss") Windows service registration started"

try {
  Assert-Admin
  Set-RunnerEnvironment
  Set-ServicePowerShellPolicy
  Invoke-Step "prepare service runner files" { New-ServiceRunnerRoot }
  Grant-ServiceRunnerAcl
  Grant-AndroidSdkAcl
  Register-ServiceRunner
  Wait-RunnerService
  Disable-StartupFallback
  Write-Log "Windows service registration complete"
} catch {
  Write-Log "Windows service registration failed: $($_.Exception.Message)"
  throw
}
