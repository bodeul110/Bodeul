[CmdletBinding()]
param(
    [string]$ProjectId = "bodeul-dev",
    [string[]]$SecretIds = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "BoDeul Cloud Run Secrets"

$gcloud = Get-Command gcloud.cmd -ErrorAction SilentlyContinue
if ($null -eq $gcloud) {
    $gcloud = Get-Command gcloud -ErrorAction Stop
}
$knownSecretIds = @(
    "bodeul-core-api-preview-db-jdbc-url",
    "bodeul-core-api-preview-db-username",
    "bodeul-core-api-preview-db-password",
    "bodeul-core-api-preview-kakao-local-rest-api-key"
)

if ($SecretIds.Count -eq 0) {
    $SecretIds = $knownSecretIds
}

$unknownSecretIds = @($SecretIds | Where-Object { $_ -notin $knownSecretIds })
if ($unknownSecretIds.Count -gt 0) {
    throw "허용되지 않은 secret ID가 있습니다: $($unknownSecretIds -join ', ')"
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

foreach ($secretId in $SecretIds) {
    gcloud secrets describe $secretId --project=$ProjectId --quiet | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "$secretId Secret Manager 리소스가 없습니다. 런북의 최초 설정을 먼저 실행하세요."
    }

    Add-SecretVersion -SecretId $secretId
}

Write-Host "Core API preview secret $($SecretIds.Count)개의 version 등록을 완료했습니다. 이 창은 닫아도 됩니다."
