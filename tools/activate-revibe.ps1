Add-Type @"
using System;
using System.Runtime.InteropServices;
public class RevibeActivate {
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hWnd);
}
"@
$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq "Revibe" } | Select-Object -First 1
if (-not $p) { Write-Output "NOTFOUND"; exit 1 }
$h = $p.MainWindowHandle
if ([RevibeActivate]::IsIconic($h)) { [void][RevibeActivate]::ShowWindow($h, 9) } else { [void][RevibeActivate]::ShowWindow($h, 5) }
[void][RevibeActivate]::SetForegroundWindow($h)
Start-Sleep -Milliseconds 400
Write-Output "$($p.Id)|$h"
