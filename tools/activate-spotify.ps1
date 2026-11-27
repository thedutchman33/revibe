Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class SpotifyWin {
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int cmd);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    public static IntPtr Find() {
        IntPtr found = IntPtr.Zero;
        EnumWindows((h, l) => {
            if (!IsWindowVisible(h)) return true;
            var title = new StringBuilder(512);
            var cls = new StringBuilder(256);
            GetWindowText(h, title, 512);
            GetClassName(h, cls, 256);
            if (cls.ToString() == "Chrome_WidgetWin_1" &&
                title.ToString().StartsWith("Spotify", StringComparison.OrdinalIgnoreCase)) {
                found = h;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
}
"@
$h = [SpotifyWin]::Find()
if ($h -eq [IntPtr]::Zero) {
    Write-Output "NOTFOUND"
    exit 1
}
[SpotifyWin]::ShowWindow($h, 9) | Out-Null
[SpotifyWin]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 400
Write-Output $h
