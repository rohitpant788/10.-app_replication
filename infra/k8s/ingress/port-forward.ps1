# Ingress Port-Forward Setup Script
# This script forwards the NGINX Ingress Controller to localhost:80
# Must be run as Administrator

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  NGINX Ingress Port-Forward Setup" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Check if running as admin
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "❌ ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "`nTo run as Administrator:" -ForegroundColor Yellow
    Write-Host "1. Right-click PowerShell" -ForegroundColor White
    Write-Host "2. Select 'Run as Administrator'" -ForegroundColor White
    Write-Host "3. Navigate to: F:\10. app_replication" -ForegroundColor White
    Write-Host "4. Run: .\infra\k8s\ingress\port-forward.ps1" -ForegroundColor White
    Write-Host "`nPress any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

Write-Host "✅ Running as Administrator`n" -ForegroundColor Green

Write-Host "Starting port-forward for NGINX Ingress Controller..." -ForegroundColor Cyan
Write-Host "Port: 80 → ingress-nginx-controller:80" -ForegroundColor White
Write-Host "`n⚠️  IMPORTANT: Keep this window open!" -ForegroundColor Yellow
Write-Host "   Closing this window will stop the port-forward.`n" -ForegroundColor Yellow

Write-Host "Access your application at:" -ForegroundColor Green
Write-Host "  • UI: http://app.local" -ForegroundColor White
Write-Host "  • Data API: http://app.local/data/cases/next-id" -ForegroundColor White
Write-Host "  • RefData API: http://app.local/refdata/countries" -ForegroundColor White
Write-Host "  • Search API: http://app.local/search/cases" -ForegroundColor White
Write-Host "  • File API: http://app.local/file/*`n" -ForegroundColor White

Write-Host "Press Ctrl+C to stop the port-forward.`n" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# Start port-forward
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80
