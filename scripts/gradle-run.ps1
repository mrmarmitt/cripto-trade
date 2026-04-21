param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleUserHome = Join-Path $repoRoot ".gradle-local"
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}

if (-not (Test-Path $gradleUserHome)) {
    New-Item -ItemType Directory -Path $gradleUserHome | Out-Null
}

$env:GRADLE_USER_HOME = $gradleUserHome

$maxAttempts = 5
$retryDelaySeconds = 5
$lockPatterns = @(
    "Could not create parent directory for lock file",
    "Timeout waiting to lock",
    "already locked by this process",
    "The process cannot access the file because it is being used by another process"
)

for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    Write-Host "gradle-run: attempt $attempt/$maxAttempts using GRADLE_USER_HOME=$gradleUserHome"

    $stdoutFile = New-TemporaryFile
    $stderrFile = New-TemporaryFile
    $process = Start-Process `
        -FilePath $gradleWrapper `
        -ArgumentList $GradleArgs `
        -NoNewWindow `
        -Wait `
        -PassThru `
        -RedirectStandardOutput $stdoutFile.FullName `
        -RedirectStandardError $stderrFile.FullName
    $exitCode = $process.ExitCode

    $output = @()
    if (Test-Path $stdoutFile.FullName) {
        $output += Get-Content $stdoutFile.FullName
    }
    if (Test-Path $stderrFile.FullName) {
        $output += Get-Content $stderrFile.FullName
    }

    Remove-Item -LiteralPath $stdoutFile.FullName -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stderrFile.FullName -Force -ErrorAction SilentlyContinue

    $output | ForEach-Object { $_ }

    if ($exitCode -eq 0) {
        exit 0
    }

    $combinedOutput = ($output | Out-String)
    $isLockFailure = $false
    foreach ($pattern in $lockPatterns) {
        if ($combinedOutput -like "*$pattern*") {
            $isLockFailure = $true
            break
        }
    }

    if (-not $isLockFailure -or $attempt -eq $maxAttempts) {
        exit $exitCode
    }

    Write-Host "gradle-run: lock detected, waiting ${retryDelaySeconds}s before retry"
    Start-Sleep -Seconds $retryDelaySeconds
}

exit 1
