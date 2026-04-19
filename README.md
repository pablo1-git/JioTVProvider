# JioTV CloudStream Plugin

A [CloudStream 3](https://github.com/recloudstream/cloudstream) plugin to watch **JioTV live channels** on Android / Android TV.

---

## ⚡ Quick Install (Repository URL)

> **This is the only installation method needed — no .cs3 file required!**

### Step 1 — Get your Repository URL

After pushing this project to GitHub and the Actions workflow runs, your URL will be:

```
https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/JioTVProvider/builds/
```

Replace `YOUR_GITHUB_USERNAME` with your actual GitHub username.

### Step 2 — Add to CloudStream

1. Open **CloudStream** app
2. Go to ☰ Menu → ⚙ **Settings** → **Extensions**
3. Tap the **"🌐 Add Repository"** or **"+"** button
4. Paste your URL:
   ```
   https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/JioTVProvider/builds/
   ```
5. Tap **Add** — CloudStream will find and install **JioTV** automatically

### Step 3 — Log In (OTP)

1. CloudStream → ⚙ Settings → Extensions → JioTV → ⚙ Settings
2. Enter your 10-digit Jio number → **Send OTP**
3. Enter the 6-digit SMS code → **Verify & Login**
4. ✅ Done! Browse channels from the home screen.

---

## 🔨 How to Publish (One-Time Setup)

### Requirements
- A free [GitHub](https://github.com) account
- Git installed on your PC (or use GitHub Desktop)

### Steps

```bash
# 1. Create a new repository on GitHub named "JioTVProvider" (public)
# 2. Push this project
cd "c:\Users\Subrata\Desktop\New folder (18)\New folder\JioTVProvider"
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/JioTVProvider.git
git push -u origin main
```

GitHub Actions will automatically:
- Build the `.cs3` plugin file
- Create a `builds` branch with `JioTVProvider.cs3` + `plugins.json`

Watch the **Actions** tab on GitHub to see the build progress (takes ~3–5 minutes).

---

## ✨ Features

| Feature | Details |
|---------|---------|
| 📺 Live channels | All JioTV channels — 600+ |
| 🗂️ Categories | Entertainment, News, Sports, Movies, Kids, Music… |
| 🔍 Search | Find any channel by name |
| 📡 Quality | Auto / High / Medium / Low |
| 🔐 Login | OTP via Jio mobile number (no password) |
| 💾 Persistent | Login persisted; no re-login needed |

---

## Disclaimer

This plugin uses the **unofficial** JioTV mobile API (same as the official Android app). For personal, educational use only.
