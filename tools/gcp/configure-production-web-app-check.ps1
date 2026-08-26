[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ProjectId,

    [Parameter(Mandatory)]
    [string]$ConfirmProjectId,

    [Parameter(Mandatory)]
    [string]$ConfirmProjectNumber,

    [Parameter(Mandatory)]
    [string]$ConfirmWebAppId,

    [Parameter(Mandatory)]
    [string]$ConfirmHostname,

    [Parameter(Mandatory)]
    [string]$OperatorAccount,

    [Parameter(Mandatory)]
    [string]$ConfirmOperatorAccount,

    [Parameter(Mandatory)]
    [ValidateSet("APPLY-PRODUCTION-WEB-APP-CHECK")]
    [string]$ConfirmApply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ExpectedProjectId = "bodeul-prod-110"
$ExpectedProjectNumber = "649312328770"
$ExpectedWebAppId = "1:649312328770:web:3ade1cb9e994abb3dea1db"
$ExpectedWebAppDisplayName = "BoDeul Admin Web Production"
$ExpectedHostname = "bodeul-admin-web-iota.vercel.app"
$DefaultAuthDomains = @(
    "bodeul-prod-110.firebaseapp.com",
    "bodeul-prod-110.web.app"
)
$ExpectedAuthDomains = @($DefaultAuthDomains + $ExpectedHostname)
$KeyDisplayName = "BoDeul Admin Web Production App Check"
$ExpectedApply = "APPLY-PRODUCTION-WEB-APP-CHECK"

if ($ProjectId -cne $ExpectedProjectId -or $ConfirmProjectId -cne $ExpectedProjectId) {
    throw "Production 프로젝트 확인값이 일치하지 않습니다."
}
if ($ConfirmProjectNumber -cne $ExpectedProjectNumber) {
    throw "Production 프로젝트 번호 확인값이 일치하지 않습니다."
}
if ($ConfirmWebAppId -cne $ExpectedWebAppId) {
    throw "Production Firebase Web 앱 확인값이 일치하지 않습니다."
}
if ($ConfirmHostname -cne $ExpectedHostname) {
    throw "관리자 웹 운영 호스트 확인값이 일치하지 않습니다."
}
if ([string]::IsNullOrWhiteSpace($OperatorAccount) -or $ConfirmOperatorAccount -cne $OperatorAccount) {
    throw "운영자 계정 확인값이 일치하지 않습니다."
}
if ($ConfirmApply -cne $ExpectedApply) {
    throw "적용 확인 문구가 일치하지 않습니다."
}

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
    $output = @()
    $exitCode = -1
    $effectiveArguments = @($Arguments)
    if ($effectiveArguments -notcontains "--quiet") {
        $effectiveArguments += "--quiet"
    }
    try {
        $ErrorActionPreference = "Continue"
        $output = & $script:GcloudExecutable @effectiveArguments "--account=$script:OperatorAccount" 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "Google Cloud 명령 실행에 실패했습니다."
    }
    return $output
}

function Invoke-GcloudJson {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $raw = Invoke-Gcloud -Arguments (@($Arguments) + "--format=json")
    $text = ($raw -join [Environment]::NewLine).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return @()
    }
    return $text | ConvertFrom-Json
}

function Invoke-GcloudJsonWithRetry {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments,

        [int]$MaxAttempts = 12
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            return Invoke-GcloudJson -Arguments $Arguments
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw "reCAPTCHA Enterprise API 준비 상태를 제한 시간 안에 확인하지 못했습니다. 같은 명령을 다시 실행하세요."
            }
            Start-Sleep -Seconds 5
        }
    }
}

function Wait-GoogleServiceEnabled {
    param(
        [Parameter(Mandatory)]
        [string]$ServiceName,

        [int]$MaxAttempts = 12
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $service = Invoke-GoogleRest -Method Get `
                -Uri "https://serviceusage.googleapis.com/v1/projects/$ExpectedProjectNumber/services/$ServiceName"
            if ([string](Get-PropertyValue -Object $service -Name "state") -ceq "ENABLED") {
                return
            }
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw
            }
        }
        Start-Sleep -Seconds 5
    }
    throw "Google Cloud API 활성 상태가 제한 시간 안에 반영되지 않았습니다. 같은 명령을 다시 실행하세요."
}

function Get-AccessToken {
    $raw = Invoke-Gcloud -Arguments @("auth", "print-access-token", "--quiet")
    $token = ($raw -join "").Trim()
    if ($token.Length -lt 20 -or $token -match "\s") {
        throw "단기 자격 증명을 확인할 수 없습니다."
    }
    return $token
}

function Invoke-GoogleRest {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("Get", "Patch", "Post")]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Uri,

        [object]$Body
    )

    $headers = @{
        Authorization = "Bearer $(Get-AccessToken)"
        "x-goog-user-project" = $ExpectedProjectId
    }
    $parameters = @{
        Headers = $headers
        Method = $Method
        Uri = $Uri
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Invoke-GoogleRestOptional {
    param(
        [Parameter(Mandatory)]
        [string]$Uri
    )

    try {
        return Invoke-GoogleRest -Method Get -Uri $Uri
    }
    catch {
        $response = Get-PropertyValue -Object $_.Exception -Name "Response"
        $statusCode = Get-PropertyValue -Object $response -Name "StatusCode"
        if ($null -ne $statusCode -and [int]$statusCode -eq 404) {
            return $null
        }
        throw
    }
}

function Get-PropertyValue {
    param(
        [object]$Object,
        [Parameter(Mandatory)]
        [string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Test-ExactStringSet {
    param(
        [object[]]$Actual,
        [object[]]$Expected
    )

    $left = @($Actual | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    $right = @($Expected | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    return $left.Count -eq $right.Count -and @(Compare-Object -ReferenceObject $left -DifferenceObject $right).Count -eq 0
}

function Assert-AppCheckRiskPolicy {
    param(
        [Parameter(Mandatory)]
        [object]$Config
    )

    $riskAnalysis = Get-PropertyValue -Object $Config -Name "riskAnalysis"
    $minValidScore = Get-PropertyValue -Object $riskAnalysis -Name "minValidScore"
    if ($null -ne $minValidScore -and [double]$minValidScore -ne 0.5) {
        throw "Firebase App Check 위험 점수 기준이 기본 production 기준과 다릅니다."
    }
}

function Assert-RecaptchaKey {
    param(
        [Parameter(Mandatory)]
        [object]$Key,

        [Parameter(Mandatory)]
        [string]$KeyId
    )

    $webSettings = Get-PropertyValue -Object $Key -Name "webSettings"
    $resourceId = ([string](Get-PropertyValue -Object $Key -Name "name")).Split("/")[-1]
    $displayName = [string](Get-PropertyValue -Object $Key -Name "displayName")
    $domains = @(Get-PropertyValue -Object $webSettings -Name "allowedDomains")
    $valid = $resourceId -ceq $KeyId -and $displayName -ceq $KeyDisplayName -and
        (Get-PropertyValue -Object $webSettings -Name "integrationType") -eq "SCORE" -and
        (Get-PropertyValue -Object $webSettings -Name "allowAllDomains") -ne $true -and
        (Get-PropertyValue -Object $webSettings -Name "allowAmpTraffic") -ne $true -and
        $null -eq (Get-PropertyValue -Object $Key -Name "testingOptions") -and
        $domains.Count -eq 1 -and $domains[0] -eq $ExpectedHostname
    if (-not $valid) {
        throw "reCAPTCHA Enterprise 키 제한이 production 기준과 다릅니다."
    }
}

$project = Invoke-GcloudJson -Arguments @("projects", "describe", $ExpectedProjectId)
if ([string]$project.projectNumber -cne $ExpectedProjectNumber -or $project.lifecycleState -cne "ACTIVE") {
    throw "Production Google Cloud 프로젝트를 확인할 수 없습니다."
}

$requiredPermissions = @(
    "resourcemanager.projects.get",
    "serviceusage.services.use",
    "firebase.clients.get",
    "firebaseappcheck.recaptchaEnterpriseConfig.get",
    "firebaseappcheck.recaptchaEnterpriseConfig.update",
    "firebaseauth.configs.get",
    "firebaseauth.configs.update",
    "recaptchaenterprise.keys.create",
    "recaptchaenterprise.keys.get",
    "recaptchaenterprise.keys.list",
    "serviceusage.services.enable",
    "serviceusage.services.get"
)
$permissionProbe = Invoke-GoogleRest -Method Post `
    -Uri "https://cloudresourcemanager.googleapis.com/v3/projects/$ExpectedProjectNumber`:testIamPermissions" `
    -Body @{ permissions = $requiredPermissions }
$grantedPermissions = @(Get-PropertyValue -Object $permissionProbe -Name "permissions")
if (@($requiredPermissions | Where-Object { $grantedPermissions -notcontains $_ }).Count -gt 0) {
    throw "Production 관리자 웹 App Check 구성에 필요한 권한이 부족합니다."
}

$webAppUri = "https://firebase.googleapis.com/v1beta1/projects/$ExpectedProjectId/webApps/$ExpectedWebAppId"
$webApp = Invoke-GoogleRest -Method Get -Uri $webAppUri
$expectedWebAppName = "projects/$ExpectedProjectId/webApps/$ExpectedWebAppId"
if ([string](Get-PropertyValue -Object $webApp -Name "name") -cne $expectedWebAppName -or
    [string](Get-PropertyValue -Object $webApp -Name "appId") -cne $ExpectedWebAppId -or
    [string](Get-PropertyValue -Object $webApp -Name "displayName") -cne $ExpectedWebAppDisplayName -or
    [string](Get-PropertyValue -Object $webApp -Name "state") -cne "ACTIVE") {
    throw "Production Firebase Web 앱이 고정 기준과 다릅니다."
}

$authUri = "https://identitytoolkit.googleapis.com/admin/v2/projects/$ExpectedProjectId/config"
$preflightAuth = Invoke-GoogleRest -Method Get -Uri $authUri
if ([string](Get-PropertyValue -Object $preflightAuth -Name "subtype") -cne "IDENTITY_PLATFORM") {
    throw "Firebase Authentication이 Identity Platform 기준과 다릅니다."
}
$preflightDomains = @(Get-PropertyValue -Object $preflightAuth -Name "authorizedDomains")
if (-not (Test-ExactStringSet -Actual $preflightDomains -Expected $DefaultAuthDomains) -and
    -not (Test-ExactStringSet -Actual $preflightDomains -Expected $ExpectedAuthDomains)) {
    throw "Firebase Auth 허용 도메인에 검토되지 않은 값이 있습니다."
}

$appCheckUri = "https://firebaseappcheck.googleapis.com/v1/projects/$ExpectedProjectNumber/apps/$ExpectedWebAppId/recaptchaEnterpriseConfig"
$preflightAppCheck = Invoke-GoogleRestOptional -Uri $appCheckUri
$preflightAppCheckExists = $null -ne $preflightAppCheck
$expectedAppCheckName = "projects/$ExpectedProjectNumber/apps/$ExpectedWebAppId/recaptchaEnterpriseConfig"
if ($preflightAppCheckExists) {
    if ([string](Get-PropertyValue -Object $preflightAppCheck -Name "name") -cne $expectedAppCheckName) {
        throw "Firebase App Check Web 설정 대상이 고정 기준과 다릅니다."
    }
    Assert-AppCheckRiskPolicy -Config $preflightAppCheck
    $preflightSiteKey = [string](Get-PropertyValue -Object $preflightAppCheck -Name "siteKey")
    $preflightTokenTtl = [string](Get-PropertyValue -Object $preflightAppCheck -Name "tokenTtl")
} else {
    $preflightSiteKey = ""
    $preflightTokenTtl = ""
}

Invoke-Gcloud -Arguments @(
    "services", "enable", "recaptchaenterprise.googleapis.com",
    "--project=$ExpectedProjectId", "--quiet"
) | Out-Null
Wait-GoogleServiceEnabled -ServiceName "recaptchaenterprise.googleapis.com"

if (-not [string]::IsNullOrWhiteSpace($preflightSiteKey)) {
    $keyId = $preflightSiteKey
} else {
    $keys = @(Invoke-GcloudJsonWithRetry -Arguments @(
        "recaptcha", "keys", "list",
        "--project=$ExpectedProjectId"
    ) | Where-Object {
        [string](Get-PropertyValue -Object $_ -Name "displayName") -ceq $KeyDisplayName
    })
    if ($keys.Count -gt 1) {
        throw "같은 표시 이름의 reCAPTCHA Enterprise 키가 여러 개입니다."
    }
    if ($keys.Count -eq 0) {
        $created = Invoke-Gcloud -Arguments @(
            "recaptcha", "keys", "create",
            "--project=$ExpectedProjectId",
            "--display-name=$KeyDisplayName",
            "--web",
            "--domains=$ExpectedHostname",
            "--integration-type=score",
            "--quiet",
            "--format=value(name)"
        )
        $keyName = ($created -join "").Trim()
    } else {
        $keyName = [string](Get-PropertyValue -Object $keys[0] -Name "name")
    }
    $keyId = $keyName.Split("/")[-1]
    if ([string]::IsNullOrWhiteSpace($keyId)) {
        throw "reCAPTCHA Enterprise 키 식별자를 확인할 수 없습니다."
    }
}
$key = Invoke-GcloudJsonWithRetry -Arguments @(
    "recaptcha", "keys", "describe", $keyId,
    "--project=$ExpectedProjectId"
)
Assert-RecaptchaKey -Key $key -KeyId $keyId

$latestAppCheck = Invoke-GoogleRestOptional -Uri $appCheckUri
$latestAppCheckExists = $null -ne $latestAppCheck
if ($latestAppCheckExists) {
    Assert-AppCheckRiskPolicy -Config $latestAppCheck
    $latestSiteKey = [string](Get-PropertyValue -Object $latestAppCheck -Name "siteKey")
    $latestTokenTtl = [string](Get-PropertyValue -Object $latestAppCheck -Name "tokenTtl")
} else {
    $latestSiteKey = ""
    $latestTokenTtl = ""
}
if ($latestAppCheckExists -ne $preflightAppCheckExists -or
    $latestSiteKey -cne $preflightSiteKey -or $latestTokenTtl -cne $preflightTokenTtl) {
    throw "Firebase App Check 설정이 사전 점검 이후 변경됐습니다. 다시 실행하세요."
}
if (-not $latestAppCheckExists -or $latestSiteKey -cne $keyId -or $latestTokenTtl -cne "3600s") {
    Invoke-GoogleRest -Method Patch `
        -Uri "$appCheckUri`?updateMask=siteKey,tokenTtl" `
        -Body @{ siteKey = $keyId; tokenTtl = "3600s" } | Out-Null
}

$latestAuth = Invoke-GoogleRest -Method Get -Uri $authUri
$latestDomains = @(Get-PropertyValue -Object $latestAuth -Name "authorizedDomains")
if (-not (Test-ExactStringSet -Actual $latestDomains -Expected $ExpectedAuthDomains)) {
    if (-not (Test-ExactStringSet -Actual $latestDomains -Expected $preflightDomains)) {
        throw "Firebase Auth 도메인이 사전 점검 이후 변경됐습니다. 다시 실행하세요."
    }
    Invoke-GoogleRest -Method Patch `
        -Uri "$authUri`?updateMask=authorizedDomains" `
        -Body @{ authorizedDomains = $ExpectedAuthDomains } | Out-Null
}

$verifiedAuth = Invoke-GoogleRest -Method Get -Uri $authUri
$verifiedAppCheck = Invoke-GoogleRest -Method Get -Uri $appCheckUri
$verifiedKey = Invoke-GcloudJsonWithRetry -Arguments @(
    "recaptcha", "keys", "describe", $keyId,
    "--project=$ExpectedProjectId"
)
Assert-RecaptchaKey -Key $verifiedKey -KeyId $keyId
Assert-AppCheckRiskPolicy -Config $verifiedAppCheck
if (-not (Test-ExactStringSet `
    -Actual @(Get-PropertyValue -Object $verifiedAuth -Name "authorizedDomains") `
    -Expected $ExpectedAuthDomains)) {
    throw "Firebase Auth 운영 도메인이 반영되지 않았습니다."
}
if ([string](Get-PropertyValue -Object $verifiedAppCheck -Name "siteKey") -cne $keyId -or
    [string](Get-PropertyValue -Object $verifiedAppCheck -Name "tokenTtl") -cne "3600s") {
    throw "Firebase App Check Web provider 설정이 반영되지 않았습니다."
}

Write-Host "Production 관리자 웹 App Check 기반 설정이 기준과 일치합니다."
