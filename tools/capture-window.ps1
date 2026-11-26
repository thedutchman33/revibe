param([string]$Title = "Revibe")

Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;

public class RevibeWin {
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }

    public static IntPtr Find(string titlePart) {
        IntPtr found = IntPtr.Zero;
        EnumWindows((hWnd, lParam) => {
            if (!IsWindowVisible(hWnd)) return true;
            StringBuilder title = new StringBuilder(512);
            GetWindowText(hWnd, title, 512);
            StringBuilder cls = new StringBuilder(256);
            GetClassName(hWnd, cls, 256);
            if (cls.ToString() == "SunAwtFrame" && title.ToString().IndexOf(titlePart, StringComparison.OrdinalIgnoreCase) >= 0) {
                found = hWnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static string Bounds(IntPtr hWnd) {
        RECT rect;
        if (!GetWindowRect(hWnd, out rect)) return "NORECT";
        return string.Format("{0},{1},{2},{3}", rect.Left, rect.Top, rect.Right - rect.Left, rect.Bottom - rect.Top);
    }
}
"@

[RevibeWin]::SetProcessDPIAware() | Out-Null
$handle = [RevibeWin]::Find($Title)
if ($handle -eq [IntPtr]::Zero) {
    Write-Output "NOTFOUND"
    exit 1
}
[RevibeWin]::ShowWindow($handle, 9) | Out-Null
[RevibeWin]::SetForegroundWindow($handle) | Out-Null
Start-Sleep -Milliseconds 900
Write-Output ([RevibeWin]::Bounds($handle))
