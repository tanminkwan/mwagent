# lib\*.jar 무결성 검증 (lib\SHA256SUMS 기준)
#
#   0: 모두 일치, 또는 Get-FileHash 가 없어 검증을 건너뜀 (경고 출력)
#   1: 파일 누락 또는 해시 불일치
#
# Get-FileHash 는 PowerShell 4.0 이상에 있다.

$sums = Join-Path 'lib' 'SHA256SUMS'
if (-not (Test-Path $sums)) {
    Write-Host "ERROR: $sums not found."
    exit 1
}
if (-not (Get-Command Get-FileHash -ErrorAction SilentlyContinue)) {
    Write-Host 'WARNING: Get-FileHash not available (PowerShell 4.0+). Skipping lib verification.'
    exit 0
}

$failed = $false
$count = 0
foreach ($line in Get-Content $sums) {
    $line = $line.Trim()
    if ($line -eq '') { continue }
    $parts = $line -split '\s+', 2
    $name = $parts[1].TrimStart('*')
    $file = Join-Path 'lib' $name
    $count++
    if (-not (Test-Path $file)) {
        Write-Host "ERROR: missing $file (run download-dependencies.bat on an online machine)"
        $failed = $true
        continue
    }
    $actual = (Get-FileHash -Algorithm SHA256 $file).Hash.ToLower()
    if ($actual -ne $parts[0].ToLower()) {
        Write-Host "ERROR: checksum mismatch: $file"
        $failed = $true
    }
}

if ($failed) { exit 1 }
Write-Host "lib verification OK ($count files)"
exit 0
