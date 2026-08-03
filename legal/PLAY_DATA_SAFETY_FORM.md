# Play Console Data Safety form — answer key

Reference for filling in Play Console → App content → Data safety. Google Play doesn't expose this via API for third-party tools, so this has to be entered manually in the Console UI — this doc maps each section to the answer, based on the actual code paths (see [[project_ai_proxy]] / [[project_no_notification_listener]] memories and `legal/PRIVACY_POLICY_HE.md` for the underlying data inventory).

Last verified against code: 2026-08-03.

## Section: Data collection and security

- **Does your app collect or share any of the required user data types?** Yes
- **Is all of the user data collected by your app encrypted in transit?** Yes — iCount API, the Cloudflare Worker AI proxy, and Firebase are all accessed over HTTPS. (The dead `backend.url`/`BuildConfig.BACKEND_URL` plain-HTTP dev default has since been removed — see commit history.)
- **Does your app provide a way for users to request that their data be deleted?** Yes — via the contact email in the privacy policy (nirgo9116@gmail.com). Also: uninstalling the app removes all on-device data (Room DB, encrypted prefs); data held by iCount must be deleted through iCount directly (call this out in the form's free-text field).
- **Account creation:** the app itself has no user-account/login system of its own — the iCount credentials are a bridge to the user's *existing* third-party iCount account, not an account created within AutoKabala. Answer "No" to "does your app require or allow account creation."

## Data types to declare

For each: **Collected?** / **Shared?** / **Purpose** / **Optional or required** / **Ephemeral processing?**

| Data type | Collected | Shared | With whom | Purpose | Required? | Ephemeral? |
|---|---|---|---|---|---|---|
| **Name** (payer name from screenshots; client names) | Yes | Yes | iCount; AI/OCR proxy (Google Vision, Gemini, OpenAI, Anthropic) | App functionality | Required (core feature) | No — persisted in local Room DB |
| **Email address** (client email; iCount account email entered by owner) | Yes | Yes (client email may be sent to iCount) | iCount | App functionality | Required | No — stored locally (iCount creds encrypted) |
| **Phone number** (client phone) | Yes | Yes | iCount (lookup); WhatsApp (via device intent, not network call) | App functionality | Required | No — persisted locally |
| **Financial info** (payment amount, transaction/receipt data) | Yes | Yes | iCount | App functionality | Required (this is the core feature) | No — persisted locally |
| **Photos** (payment-confirmation screenshot) | Yes | Yes | AI/OCR proxy (Vision, Gemini, OpenAI, Anthropic) — for text extraction only | App functionality | Required | Yes — image is cached temporarily (overwritten on next share), not retained; only the *extracted text* is persisted, not the image itself |
| **Calendar** (event title/time/description, if calendar sync enabled) | Yes | No | — (device-only, never transmitted) | App functionality | Optional (only if user enables calendar sync) | No — persisted on-device, auto-purged after ~60 days |
| **App activity** (basic usage events, e.g. app opens) | Yes | Yes | Firebase (Google) | Analytics | Optional | — |
| **App info and performance** (crash logs / diagnostics) | Yes | Yes | Firebase Crashlytics (Google) | Analytics (crash reporting) | Optional | — |
| **Device or other IDs** (Firebase installation ID, standard auto-collected) | Yes | Yes | Firebase (Google) | Analytics | Optional | — |

Data types **not** collected — answer "No" / leave unchecked: Location, Web browsing history, Messages/SMS, Contacts, Health & fitness, Audio, Files & docs, Purchase history beyond the payments described above, User IDs beyond the above.

## Notes / gotchas for the form's free-text boxes

- When the form asks *why* Name/Financial info/Photos are shared with third parties, use wording like: "Sent to a third-party invoicing service (iCount) chosen by the user to generate a receipt, and to AI/OCR providers solely to extract text from a screenshot the user chose to share — not used for advertising or sold to any party."
- The AI proxy (Cloudflare Worker) itself does not need to be listed as a separate data recipient in the form — it's a stateless pass-through with no storage; list the actual AI providers (Google, OpenAI, Anthropic) as the recipients, since that's who Google will expect to see for OCR/LLM processing.
- If a reviewer flags "why does a receipts app read Calendar" — the free-text justification is: optional feature to create/read local reminder events for scheduled client payments; never transmitted off-device.
