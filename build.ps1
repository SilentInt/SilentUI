param(
    [string]$Root = 'C:\Users\Silent\doushen_hly\SilentUI',
    [switch]$Release
)

$ErrorActionPreference = 'Stop'

$root = $Root
$appMain = Join-Path $root 'app\src\main'

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Users\Silent\Downloads\eink\android-sdk' }
$bt = Join-Path $sdk 'build-tools\36.0.0'
$platform = Join-Path $sdk 'platforms\android-36'
$androidJar = Join-Path $platform 'android.jar'

if (-not (Test-Path $androidJar)) { throw "android.jar not found: $androidJar" }
if (-not (Test-Path (Join-Path $bt 'aapt2.exe'))) { throw "aapt2 not found under $bt" }

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { throw 'JAVA_HOME not set' }
$jbin = Join-Path $javaHome 'bin'

$build = Join-Path $root 'build'
$gen = Join-Path $build 'gen'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build, $gen, $classes, $dex | Out-Null

$aapt2 = Join-Path $bt 'aapt2.exe'
$d8 = Join-Path $bt 'd8.bat'
$apksigner = Join-Path $bt 'apksigner.bat'
$zipalign = Join-Path $bt 'zipalign.exe'
$javac = Join-Path $jbin 'javac.exe'
$jar = Join-Path $jbin 'jar.exe'
$keytool = Join-Path $jbin 'keytool.exe'

$res = Join-Path $appMain 'res'
$manifest = Join-Path $appMain 'AndroidManifest.xml'
$srcDir = Join-Path $appMain 'java'

Write-Output '== aapt2 compile =='
& $aapt2 compile --dir $res -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed with exit code $LASTEXITCODE" }

Write-Output '== aapt2 link =='
& $aapt2 link -o (Join-Path $build 'base.apk') `
  -I $androidJar `
  --manifest $manifest `
  -R (Join-Path $build 'res.zip') `
  --java $gen `
  --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed with exit code $LASTEXITCODE" }

Write-Output '== javac =='
$src = @(Get-ChildItem $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$rjava = @(Get-ChildItem $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($src.Count -eq 0) { throw 'no java sources found' }
$sources = @($src) + @($rjava)
& $javac --release 8 -Xlint:-options -classpath $androidJar -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
if (-not (Get-ChildItem $classes -Recurse -Filter *.class)) { throw 'javac produced no class files' }

Write-Output '== jar =='
& $jar cf (Join-Path $build 'classes.jar') -C $classes .

Write-Output '== d8 =='
& $d8 --release --lib $androidJar --output $dex (Join-Path $build 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw "d8 failed with exit code $LASTEXITCODE" }
if (-not (Test-Path (Join-Path $dex 'classes.dex'))) { throw 'd8 did not produce classes.dex' }

Write-Output '== add classes.dex =='
Copy-Item (Join-Path $build 'base.apk') (Join-Path $build 'unsigned.apk') -Force
& $jar uf (Join-Path $build 'unsigned.apk') -C $dex classes.dex
if ($LASTEXITCODE -ne 0) { throw "jar update failed with exit code $LASTEXITCODE" }

Write-Output '== zipalign =='
& $zipalign -f 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw "zipalign failed with exit code $LASTEXITCODE" }

Write-Output '== keystore =='
$ks = Join-Path $root 'debug.keystore'
$ksAlias = 'androiddebugkey'
$ksPass = 'android'
$keyPass = 'android'
$outName = if ($Release) { 'SilentUI-release.apk' } else { 'SilentUI.apk' }
if (-not (Test-Path $ks)) {
  & $keytool -genkeypair -keystore $ks -storepass $ksPass -keypass $keyPass `
    -alias $ksAlias -dname 'CN=Android Debug,O=Android,C=US' `
    -keyalg RSA -keysize 2048 -validity 10000
  if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
}

Write-Output '== apksigner =='
$out = Join-Path $root $outName
& $apksigner sign --ks $ks --ks-pass pass:$ksPass --key-pass pass:$keyPass --out $out (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw "apksigner failed with exit code $LASTEXITCODE" }

Write-Output '== verify =='
& $apksigner verify --verbose $out
Write-Output "APK: $out"
Get-Item $out | Select-Object FullName, Length
