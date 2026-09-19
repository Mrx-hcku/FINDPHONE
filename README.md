# Phone Finder (Telegram-controlled anti-theft tracker)

## Architecture (after FCM bridge)
```
Telegram (/locate) → Telegram webhook → Cloud Function (telegramWebhook)
                                            → looks up device in Firestore
                                            → sends FCM data push
                                                → phone wakes (even if the app
                                                  process was killed)
                                                → fetches location
                                                → sends it straight back via
                                                  Telegram Bot API (sendLocation)
```
FCM wakes a killed process too, as long as the user hasn't manually
**Force Stopped** the app from Settings — Android blocks every wake path,
FCM included, once that happens. No other fix exists for that specific case
without root.

## One-time project setup (you, before selling)
1. Firebase project already set up, `app/google-services.json` is already in
   this project (package `com.tracker.phonefinder`). Nothing to do here.
2. Enable **Firestore** (Native mode) in the Firebase console for project
   `kalua-9b39c` if you haven't already.
3. Install the Firebase CLI locally once (`npm install -g firebase-tools`),
   `firebase login`, `firebase use --add` (pick your project), then from the
   project root: `firebase deploy --only functions,firestore:rules`.
4. Note the deployed function base URL, e.g.
   `https://us-central1-yourproject.cloudfunctions.net`
   → paste it into `Constants.CLOUD_FUNCTION_BASE_URL` in the Android code.

## Build the APK via GitHub Actions
1. Push this whole folder as a new repo (`main` branch) — `app/google-services.json`
   is already included, no secret setup needed.
2. Actions tab → "Build APK" runs automatically, or trigger manually.
3. Download the `app-debug-apk` artifact once the run finishes.

## Per-customer setup (repeat for each paid user)
1. They message `@BotFather` → `/newbot` → get their own bot token.
2. Message the new bot once, then open
   `https://api.telegram.org/bot<TOKEN>/getUpdates` → read `"chat":{"id":...}`
   → that's their chat ID.
3. **Set the webhook** (do this once per bot, from anywhere — curl/Postman):
   ```
   https://api.telegram.org/bot<TOKEN>/setWebhook?url=<CLOUD_FUNCTION_BASE_URL>/telegramWebhook/<TOKEN>
   ```
4. In the app's onboarding screen, enter that same bot token + chat ID →
   "Enable Protection". The app registers its FCM token with the Cloud
   Function automatically at this point.

## Commands (send from the paired Telegram chat)
- `/locate` — routed through the Cloud Function → FCM, works even if the app
  was killed. If device location is OFF, the app sends the last known fix
  immediately and auto-sends a fresh one the moment location is turned back on.

## System-app disguise
Shows in the launcher as "System Services" with a generic gear icon
(activity-alias `.LauncherAlias`). "Hide App Icon" button disables that alias
so it disappears from the app drawer entirely — the service keeps running.
Find it again later via Settings → Apps → System Services.

## Known hard limit
If the app is manually **Force Stopped** (Settings → Apps → Force Stop),
Android blocks all wake mechanisms — boot receiver, watchdog alarm, and FCM —
until the app is opened again by hand or the device reboots. No app-level
workaround exists for this without root.

## Before selling to paid users
- Move `bot_token`/`chat_id` from plain SharedPreferences to
  `androidx.security:security-crypto` EncryptedSharedPreferences.
- Lock down the `registerToken` Cloud Function further (e.g. require a shared
  secret header) so randoms can't spam Firestore with fake device rows.
- Add a license-key/subscription check before the service is allowed to start.
