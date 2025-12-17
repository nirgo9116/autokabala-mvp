# AutoKabala – Android MVP

## What this app does
AutoKabala is an Android app that listens to Bit and PayBox payment
notifications on the device.

When a payment notification is received, the app extracts:
- Payer name
- Payment amount

The data is then sent via an HTTP POST request to a backend webhook,
which will later generate a receipt (iCount / Morning).

## Current status (important)
- NotificationListenerService is implemented
- App successfully receives payment notifications
- Basic notification parsing exists (not fully reliable yet)
- Webhook POST integration exists (needs verification and hardening)
- No receipt generation yet (backend responsibility)

## What is NOT done yet
- Reliable parsing for all Bit notification formats
- Reliable parsing for PayBox notifications
- Retry mechanism when webhook call fails
- Offline queue (when device has no internet)
- Security hardening (tokens, encryption)
- User authentication
- Production-ready UI
- Play Store preparation and compliance

## How to run the app
1. Open the project in Android Studio
2. Let Gradle sync completely
3. Run on an emulator or physical Android device
4. Enable Notification Access for the app in Android settings
5. Trigger a test payment notification

## Configuration (IMPORTANT – no secrets in repo)
- Webhook URL should be stored in local configuration
- API keys and tokens must NOT be committed to Git
- Use placeholders only inside the repository

## Android details
- Language: Kotlin
- Architecture: Service-based MVP
- Min SDK: (fill in)
- Target SDK: (fill in)

## Notes
This project is an MVP.
Code quality, error handling, and security are intentionally minimal
and will be improved in later stages.
