param([int]$X, [int]$Y, [int]$Count = 1)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MouseClick {
    [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr value);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT point);
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    public static void Click() {
        mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
        System.Threading.Thread.Sleep(60);
        mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
    }
}
"@

[MouseClick]::SetProcessDpiAwarenessContext([IntPtr](-4)) | Out-Null
[MouseClick]::SetCursorPos($X, $Y) | Out-Null
Start-Sleep -Milliseconds 220
$point = New-Object MouseClick+POINT
[MouseClick]::GetCursorPos([ref]$point) | Out-Null
Write-Output "cursor $($point.X),$($point.Y)"
for ($i = 0; $i -lt $Count; $i++) {
    [MouseClick]::Click()
    Start-Sleep -Milliseconds 120
}
Write-Output "clicked $X,$Y x$Count"
