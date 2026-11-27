$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq "Revibe" } | Select-Object -First 1
if ($p) { $p.CloseMainWindow() | Out-Null; Write-Output "closed $($p.Id)" } else { Write-Output "NOTFOUND" }
