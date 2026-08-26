[CmdletBinding()]
param(
    [string]$ProjectId = "bodeul-prod-110",
    [Parameter(Mandatory)]
    [string]$ConfirmProjectId,
    [Parameter(Mandatory)]
    [string]$ConfirmProjectNumber,
    [Parameter(Mandatory)]
    [string]$ConfirmDatabase,
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$OperatorAccount,
    [Parameter(Mandatory)]
    [string]$ConfirmOperatorAccount,
    [Parameter(Mandatory)]
    [ValidateSet("ENABLE-PRODUCTION-FIRESTORE-PITR")]
    [string]$ConfirmApply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ExpectedProjectId = "bodeul-prod-110"
$ExpectedProjectNumber = "649312328770"
$ExpectedDatabase = "(default)"
$ExpectedLocation = "asia-northeast1"
$ExpectedType = "FIRESTORE_NATIVE"
$ExpectedRetentionPeriod = "604800s"

$gcloud = Get-Command gcloud.cmd -ErrorAction SilentlyContinue
if ($null -eq $gcloud) {
    $gcloud = Get-Command gcloud -ErrorAction Stop
}
$script:GcloudExecutable = $gcloud.Source
$script:OperatorAccount = $OperatorAccount

function Invoke-Gcloud {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $script:GcloudExecutable @Arguments "--account=$script:OperatorAccount" 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        $operation = ($Arguments | Select-Object -First 6) -join " "
        throw "gcloud 명령을 완료하지 못했습니다(exit=$exitCode, operation=$operation). 현재 계정 권한과 Firestore 상태를 확인하세요."
    }
    return @($output)
}

function ConvertFrom-GcloudJson {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $json = (Invoke-Gcloud -Arguments $Arguments) -join [Environment]::NewLine
    if ([string]::IsNullOrWhiteSpace($json)) {
        throw "Google Cloud 리소스 확인 결과가 비어 있습니다."
    }
    return $json | ConvertFrom-Json
}

function Get-FirestoreDatabase {
    return ConvertFrom-GcloudJson -Arguments @(
        "firestore", "databases", "describe",
        "--database=$ExpectedDatabase",
        "--project=$ProjectId",
        "--format=json(name,locationId,type,deleteProtectionState,pointInTimeRecoveryEnablement,versionRetentionPeriod)",
        "--quiet"
    )
}

function Assert-FixedDatabase {
    param(
        [Parameter(Mandatory)]
        [object]$Database
    )

    $expectedName = "projects/$ExpectedProjectId/databases/$ExpectedDatabase"
    if ($Database.name -cne $expectedName -or
        $Database.locationId -cne $ExpectedLocation -or
        $Database.type -cne $ExpectedType -or
        $Database.deleteProtectionState -cne "DELETE_PROTECTION_ENABLED") {
        throw "Firestore 대상이 고정 production database 계약과 일치하지 않습니다."
    }
}

if ($ProjectId -cne $ExpectedProjectId -or
    $ConfirmProjectId -cne $ExpectedProjectId -or
    $ConfirmProjectNumber -cne $ExpectedProjectNumber -or
    $ConfirmDatabase -cne $ExpectedDatabase -or
    $ConfirmOperatorAccount -cne $OperatorAccount -or
    $ConfirmApply -cne "ENABLE-PRODUCTION-FIRESTORE-PITR") {
    throw "Production Firestore PITR 대상 재입력값이 일치하지 않습니다."
}

$accessToken = (Invoke-Gcloud -Arguments @("auth", "print-access-token", "--quiet")) -join ""
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw "지정한 Google Cloud 실행 계정의 access token을 확인하지 못했습니다."
}
$accessToken = $null

$project = ConvertFrom-GcloudJson -Arguments @(
    "projects", "describe", $ProjectId, "--format=json(projectId,projectNumber)", "--quiet"
)
if ($project.projectId -cne $ExpectedProjectId -or
    $project.projectNumber.ToString() -cne $ExpectedProjectNumber) {
    throw "gcloud가 확인한 Production 프로젝트가 고정 대상과 일치하지 않습니다."
}

$database = Get-FirestoreDatabase
Assert-FixedDatabase -Database $database

if ($database.pointInTimeRecoveryEnablement -cne "POINT_IN_TIME_RECOVERY_ENABLED") {
    Invoke-Gcloud -Arguments @(
        "firestore", "databases", "update",
        "--database=$ExpectedDatabase",
        "--project=$ProjectId",
        "--enable-pitr",
        "--quiet",
        "--format=none"
    ) | Out-Null
}

$verifiedDatabase = Get-FirestoreDatabase
Assert-FixedDatabase -Database $verifiedDatabase
if ($verifiedDatabase.pointInTimeRecoveryEnablement -cne "POINT_IN_TIME_RECOVERY_ENABLED" -or
    $verifiedDatabase.versionRetentionPeriod -cne $ExpectedRetentionPeriod) {
    throw "Firestore PITR 또는 7일 version 보존 설정이 활성 기준과 일치하지 않습니다."
}

Write-Host "Production Firestore PITR와 7일 version 보존 활성화를 확인했습니다."
