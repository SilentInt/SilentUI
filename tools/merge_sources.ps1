param(
    [string]$Root = 'C:\Users\Silent\doushen_hly\SilentUI',
    [string]$SuMain = 'C:\Users\Silent\Downloads\eink\SuGesture\app\src\main',
    [string]$CssRoot = 'C:\Users\Silent\doushen_hly\customstatusbar'
)

$ErrorActionPreference = 'Stop'

# 把两个原始项目的 Java 源码复制到 SilentUI 统一包名 com.silentui。
# 这个脚本是一次性生成器：生成完成后，SilentUI/app/src/main 即为自包含源码。
$root = $Root
$appMain = Join-Path $root 'app\src\main'
$javaDir = Join-Path $appMain 'java\com\silentui'

$suMain = $SuMain
$suDir = Join-Path $suMain 'java\com\sugesture'

$cssRoot = $CssRoot
$cssDir = Join-Path $cssRoot 'src\com\example\customstatusbar'

if (-not (Test-Path $suDir)) { throw "SuGesture source not found: $suDir" }
if (-not (Test-Path $cssDir)) { throw "customstatusbar source not found: $cssDir" }

New-Item -ItemType Directory -Force -Path $javaDir | Out-Null

function Write-Utf8NoBom($path, $text) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $utf8)
}

function Convert-SuFile($srcFile, $dstName) {
    $text = [System.IO.File]::ReadAllText($srcFile)
    $text = $text -replace 'package com\.sugesture;', 'package com.silentui;'
    $text = $text -replace 'com\.sugesture', 'com.silentui'
    $text = $text -replace 'SuGesture', 'SilentUI'

    # 守护进程相关文件路径/文件名
    $text = $text -replace '/data/local/tmp/sugesture\.conf', '/data/local/tmp/silentui.conf'
    $text = $text -replace '/data/local/tmp/sugestured\.log', '/data/local/tmp/silentui.log'
    $text = $text -replace '/data/local/tmp/sugestured\.pid', '/data/local/tmp/silentui.pid'
    $text = $text -replace '/data/adb/service\.d/sugesture\.sh', '/data/adb/service.d/silentui.sh'
    $text = $text -replace '"sugesture\.conf"', '"silentui.conf"'
    $text = $text -replace '"sugesture\.sh"', '"silentui.sh"'
    $text = $text -replace '"su_gesture"', '"silentui_gesture"'
    $text = $text -replace '"su_gesture\.log"', '"silentui.log"'

    if ($dstName -eq 'GestureActivity.java') {
        $text = $text -replace 'public class MainActivity extends Activity', 'public class GestureActivity extends Activity'
    }

    $dst = Join-Path $javaDir $dstName
    Write-Utf8NoBom $dst $text
    Write-Output ("generated: " + (Resolve-Path $dst))
}

function Convert-CssFile($srcFile, $dstName) {
    $text = [System.IO.File]::ReadAllText($srcFile)
    $text = $text -replace 'package com\.example\.customstatusbar;', 'package com.silentui;'
    $text = $text -replace 'com\.example\.customstatusbar', 'com.silentui'

    if ($dstName -eq 'StatusBarActivity.java') {
        $text = $text -replace 'public class MainActivity extends Activity', 'public class StatusBarActivity extends Activity'
        $text = $text -replace 'MainActivity\.this', 'StatusBarActivity.this'
    }

    if ($dstName -eq 'StatusBarService.java') {
        # targetSdk 34+ 的前台服务需要显式类型
        $text = $text.Replace('import android.content.pm.ApplicationInfo;', "import android.content.pm.ApplicationInfo;`nimport android.content.pm.ServiceInfo;")

        $fg = @'
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1001, n);
            }
'@
        $text = $text -replace 'startForeground\(1001, n\);', $fg

        # Android 12+ 未授予 BLUETOOTH_CONNECT 时，读取蓝牙状态会抛 SecurityException
        $text = $text -replace 'boolean btOn = bluetoothAdapter != null && bluetoothAdapter.isEnabled\(\);', 'boolean btOn = btEnabled();'

        $safeBt = @'
    private boolean btEnabled() {
        try {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

'@
        $text = $text -replace '    private String btDeviceName\(\) \{', ($safeBt + '    private String btDeviceName() {')
    }

    $dst = Join-Path $javaDir $dstName
    Write-Utf8NoBom $dst $text
    Write-Output ("generated: " + (Resolve-Path $dst))
}

Get-ChildItem -LiteralPath $suDir -Filter *.java | ForEach-Object {
    if ($_.Name -eq 'MainActivity.java') {
        Convert-SuFile $_.FullName 'GestureActivity.java'
    } else {
        Convert-SuFile $_.FullName $_.Name
    }
}

Get-ChildItem -LiteralPath $cssDir -Filter *.java | ForEach-Object {
    if ($_.Name -eq 'MainActivity.java') {
        Convert-CssFile $_.FullName 'StatusBarActivity.java'
    } else {
        Convert-CssFile $_.FullName $_.Name
    }
}

Write-Output 'merge_sources.ps1 done.'
