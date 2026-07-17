[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ProjectId,
    [string[]]$SecretIds = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "BoDeul Production Cloud Run Secrets"

if ($ProjectId -eq "bodeul-dev" -or $ProjectId -notmatch '^bodeul-prod-[a-z0-9-]+$') {
    throw "production project ID 형식이 아니거나 개발 프로젝트입니다."
}

$confirmedProjectId = Read-Host "production project ID를 다시 입력하세요"
if ($confirmedProjectId -ne $ProjectId) {
    throw "입력한 production project ID가 일치하지 않습니다."
}

$gcloud = Get-Command gcloud -ErrorAction Stop
$knownSecretIds = @(
    "bodeul-core-api-production-db-jdbc-url",
    "bodeul-core-api-production-db-username",
    "bodeul-core-api-production-db-password",
    "bodeul-core-api-production-kakao-local-rest-api-key"
)

if ($SecretIds.Count -eq 0) {
    $SecretIds = $knownSecretIds
}

$unknownSecretIds = @($SecretIds | Where-Object { $_ -notin $knownSecretIds })
if ($unknownSecretIds.Count -gt 0) {
    throw "허용되지 않은 production secret ID가 있습니다: $($unknownSecretIds -join ', ')"
}

function Add-SecretVersion {
    param(
        [Parameter(Mandatory)]
        [string]$SecretId
    )

    $secureValue = Read-Host "$SecretId 값" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)

    try {
        $plainValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        if ([string]::IsNullOrEmpty($plainValue)) {
            throw "$SecretId 값이 비어 있습니다."
        }

        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardInput = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.CreateNoWindow = $true

        if ($gcloud.Source.EndsWith(".cmd", [StringComparison]::OrdinalIgnoreCase)) {
            $startInfo.FileName = $env:ComSpec
            $commandLine = '"{0}" secrets versions add "{1}" --data-file=- --project="{2}" --quiet' -f `
                $gcloud.Source, $SecretId, $ProjectId
            $startInfo.Arguments = '/d /s /c "' + $commandLine + '"'
        }
        else {
            $startInfo.FileName = $gcloud.Source
            $startInfo.Arguments = "secrets versions add $SecretId --data-file=- --project=$ProjectId --quiet"
        }

        $process = [Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            throw "gcloud 프로세스를 시작하지 못했습니다."
        }

        $process.StandardInput.Write($plainValue)
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        if ($process.ExitCode -ne 0) {
            throw "Secret Manager version 추가 실패: $stderr"
        }

        Write-Host "$SecretId 새 version을 등록했습니다."
        if (-not [string]::IsNullOrWhiteSpace($stdout)) {
            Write-Verbose $stdout.Trim()
        }
    }
    finally {
        if ($null -ne $bstr -and $bstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
        $plainValue = $null
        $secureValue = $null
    }
}

$activeAccount = gcloud auth list --filter=status:ACTIVE --format="value(account)"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($activeAccount)) {
    throw "활성화된 gcloud 계정을 확인하지 못했습니다."
}

gcloud projects describe $ProjectId --format="value(projectId)" --quiet | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "production Google Cloud 프로젝트를 확인하지 못했습니다."
}

foreach ($secretId in $SecretIds) {
    gcloud secrets describe $secretId --project=$ProjectId --quiet | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "$secretId Secret Manager 리소스가 없습니다. production 최초 설정을 먼저 실행하세요."
    }

    Add-SecretVersion -SecretId $secretId
}

Write-Host "Core API production secret $($SecretIds.Count)개의 version 등록을 완료했습니다. 이 창은 닫아도 됩니다."
