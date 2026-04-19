$repoPath = "c:\Users\Subrata\Desktop\New folder (18)\New folder\JioTVProvider"
Set-Location $repoPath

Write-Host "=== Step 1: Initializing git ===" -ForegroundColor Cyan
git init

Write-Host "`n=== Step 2: Staging all files ===" -ForegroundColor Cyan
git add .

Write-Host "`n=== Step 3: Creating initial commit ===" -ForegroundColor Cyan
git commit -m "Initial commit: JioTV CloudStream plugin with OTP login"

Write-Host "`n=== Step 4: Setting branch to main ===" -ForegroundColor Cyan
git branch -M main

Write-Host "`n=== Step 5: Adding remote origin ===" -ForegroundColor Cyan
git remote add origin https://github.com/SubCoder/JioTVProvider.git

Write-Host "`n=== Step 6: Pushing to GitHub ===" -ForegroundColor Cyan
Write-Host "(A login popup may appear - sign in with your GitHub account)" -ForegroundColor Yellow
git push -u origin main

Write-Host "`n=== Done! ===" -ForegroundColor Green
Write-Host "Repository URL: https://github.com/SubCoder/JioTVProvider" -ForegroundColor Green
Write-Host "Check Actions tab for build progress: https://github.com/SubCoder/JioTVProvider/actions" -ForegroundColor Green
