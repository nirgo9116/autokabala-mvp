# AutoKabala (Receipt Bot Lite)

AutoKabala is a **native Android application** that automatically processes payment notifications (Bit, PayBox, etc.) and helps self‑employed users generate and manage receipts.

The app is **not a web app**.  
It is a fully installed Android application with native background capabilities, using a WebView only for its UI layer.

---

## 📱 What This App Is

- A **native Android app** written in **Kotlin**
- Installed on the user’s phone via **APK / Google Play**
- Uses **Android system APIs**:
    - `NotificationListenerService`
    - System permissions
    - Background processing
- Core business logic is **100% native**

The UI is rendered using a **local WebView**, bundled inside the APK.

---

## 🧠 Architecture Overview

The app is intentionally split into **three clear layers**:

### 1️⃣ Native Core (Android / Kotlin)
Responsible for:
- Listening to payment notifications
- Processing and validating events
- Enforcing enable / disable rules
- Ensuring Play Store compliance

Key components:
- `AutoKabalaNotificationService`
- `ListenerManager`

### 2️⃣ UI Layer (WebView)
Responsible for:
- Screens, layout, and UX
- Auth, dashboard, settings, onboarding
- Fast UI iteration

The UI is:
- Built as a **web app** (Lovable)
- Compiled into static files (`dist/`)
- Loaded **locally** from app assets:
