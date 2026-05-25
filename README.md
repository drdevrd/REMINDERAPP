# Reminder App

## What it does
1. Tap app → 4-step setup:
   - **Step 1** — What to remind (type text)
   - **Step 2** — Start time (time picker)
   - **Step 3** — Repeat every X minutes (1/2/3/5/10/15/30)
   - **Step 4** — Ring for how long (10s/20s/30s/1min/2min/5min)
2. At start time → notification RINGS with alarm sound + vibration for set duration
3. Repeats every X minutes until you tap STOP
4. STOP button in app and in notification bar

---

## Build APK via GitHub Actions

### Step 1 — Create GitHub repo
1. Go to https://github.com → New repository
2. Name: `ReminderApp` → Create

### Step 2 — Upload files
1. Upload this ZIP and extract (keep folder structure intact)
2. Commit all files

### Step 3 — Run build
1. Go to repo → **Actions** tab
2. Click **Build APK** → **Run workflow** → **Run workflow**
3. Wait ~5 minutes

### Step 4 — Download APK
1. Click the finished run
2. Scroll to **Artifacts** → click **Reminder-APK**
3. Unzip → `app-debug.apk`

### Step 5 — Install on phone
1. Send APK to your phone
2. Tap to install
3. If blocked: Settings → Security → Install unknown apps → Allow

---

## First run on Android 12+
The app will ask you to allow **Exact Alarms** in Settings.
This is required so the notification rings at the exact time you set.
