# lib\*.jar 무결성 검증 (lib\SHA256SUMS 기준)
#
#   0: 모두 일치, 또는 Get-FileHash 가 없어 검증을 건너뜀 (경고 출력)
#   1: 파일 누락, 해시 불일치, 또는 SHA256SUMS 에 없는 jar 가 lib\ 에 남아 있음
#      (빌드·배포 스크립트는 lib\*.jar 를 모두 classpath 에 넣으므로, 버전을 올린 뒤 남은
#       옛 jar 는 클래스 충돌을 일으킨다)
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
$listed = @{}
foreach ($line in Get-Content $sums) {
    $line = $line.Trim()
    if ($line -eq '') { continue }
    $parts = $line -split '\s+', 2
    $name = $parts[1].TrimStart('*')
    $listed[$name.ToLower()] = $true
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

# SHA256SUMS 에 없는 jar (옛 버전 등)
foreach ($jar in Get-ChildItem -Path 'lib' -Filter '*.jar') {
    if (-not $listed.ContainsKey($jar.Name.ToLower())) {
        Write-Host "ERROR: lib\$($jar.Name) is not listed in $sums (old version?). Remove it."
        $failed = $true
    }
}

if ($failed) { exit 1 }
Write-Host "lib verification OK ($count files)"
exit 0
