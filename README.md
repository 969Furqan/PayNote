# PayNote

An expense tracker that tracks and logs incoming and outgoing transactions through SMS notifications and bank app alerts. PayNote runs on Android 8.0+ and stores its history locally with Room. No bank login or Shizuku installation is required.

Supported notification formats include **SadaPay**, **NayaPay**, **JazzCash**, **Meezan Bank**, **Google Pay / Wallet**, and bank SMS alerts shown by supported messaging apps. App notifications are preferred, with SMS as a fallback. Detection depends on the notification text the provider sends.

## Key Features
1. **Shizuku-Style Inline Prompt (`RemoteInput`)**: Immediately upon detecting a payment or transfer, PayNote posts a high-priority notification showing the extracted **Amount**, **Direction (Sent / Paid / Received)**, **Counterparty Name**, and **TID**. Tap **Add Purpose** to type the purpose directly in the notification shade without leaving your current app.
2. **3-Layer Deduplication Engine**:
   - **Layer 1 (TID / STAN / RRN Suffix & Exact Match)**: Matches transaction reference IDs across App push notifications and SMS alerts even if one channel zero-padded or truncated the TID.
   - **Layer 2 (3-Minute Cross-Channel Sliding Window)**: If an App push notification and an SMS alert arrive within 3 minutes with the exact same amount and compatible direction/counterparty, the second prompt is suppressed and any newly discovered metadata (such as a TID present only in the SMS) is merged into the existing transaction record.
     Different banks/wallets, currencies, or conflicting transaction references remain separate. An unidentified SMS needs a matching reference before it can be merged with a named bank's transaction. Notification processing is serialized so simultaneous app/SMS alerts cannot insert the same transaction twice.
   - **Layer 3 (Non-Transaction Filtering)**: Automatically ignores OTPs, login alerts, failed/declined alerts, and promotional messages.
3. **Built-in Simulator**: Test SadaPay + SMS duplicate suppression, Meezan Bank IBFT, and Google Wallet prompts from **Settings → Test notifications**.
4. **App notifications first**: Bank/wallet app alerts are processed immediately. SMS alerts wait five seconds as a fallback. If an app alert arrives after an SMS, its counterparty, provider and direction become the preferred details on the same record, preserving any saved purpose. An SMS cannot replace a valid app counterparty just because its text is longer. Missing names can be matched across channels only when the same bank/value/direction has one unambiguous recent candidate.
5. **Clear transaction direction**: Activity rows show Received/Sent/Paid and From/To/At labels. Transaction details explicitly show Incoming/Outgoing and the preferred App notification/SMS fallback source.

## App navigation

- **Home** shows incoming/outgoing activity for this month in one selected currency, entries still needing a purpose, and recent transactions. These totals describe recorded movement, not bank balances.
- **Activity** supports search plus filters for wallet, category, incoming/outgoing, date period, purpose status and currency, with newest/oldest ordering. Export shares only the currently filtered entries as a CSV file.
- **Wallets** groups the detected providers. Open a wallet to see its recorded incoming/outgoing totals per currency and its latest entries.
- **Transaction details** show the full counterparty, category, direction, source, reference and original notification. Use **Edit entry** to correct the counterparty, wallet/source name, category, direction or purpose. Corrections are stored separately from the original notification and are preserved when another alert arrives. Export one entry, or delete with an Undo action.
- **Settings** provides persistent System/Light/Dark appearance, notification permissions, full-history CSV export, a transfer explanation, and sample-notification tools.

The interface uses Material Rounded icons, native Compose spring/slide/fade transitions, and a custom adaptive launcher icon. Notification actions open the corresponding transaction detail page.

### Manual entries and home-screen widget

Tap **+** on Home or Activity to add a missed transaction. Choose Incoming or Outgoing, enter the amount, currency, counterparty and wallet/source, and optionally add a category and purpose. You can also set the transaction date and time. Category names and wallet names can be customised.

In **Settings → Home screen widget**, add PayNote to your launcher. The widget shows the current month's recorded money in/out in one currency (PKR when present), follows the phone's light/dark appearance, and refreshes after history changes. **Incoming** and **Outgoing** open a compact entry panel over the launcher without entering the main app. You can also long-press your home screen and select **Widgets → PayNote**.

Manual records are labelled **Manual entry** and remain separate from later detected alerts. They are included in filters, wallet totals and CSV exports. The version 1 → 2 database migration adds correction/category columns without deleting existing history.

### Transfers between your own wallets

A transfer from one of your apps to another produces an **Outgoing** record in the sender and an **Incoming** record in the receiver when both notifications are received. Cross-channel app/SMS duplicates are merged within each wallet. The two wallet entries remain separate; automatic linking as an internal transfer is not implemented. Home totals include both sides, so they must not be interpreted as income or spending alone. Tap the transfer explanation on Home or in Settings for an example.

## How to Open & Build in Android Studio
1. Extract `PayNote_Android_Project.zip`.
2. Open **Android Studio** (Iguana / Jellyfish / Koala or newer) and select **Open** -> choose the `PayNote` folder.
3. Let Gradle sync complete, then click **Run ('app')** on your Android device (API 26+).
4. Use **Finish setup** on Home or **Settings → Notification access / Purpose prompts** to grant the two notification permissions. Optional sample prompts are in **Settings → Test notifications**; they create sample entries in your history without making payments.

## Command-line build and device checks

Use JDK 17 and an Android SDK with Platform 34 and Build Tools 34.0.0. Set `JAVA_HOME` to the JDK folder and either set `ANDROID_HOME` or put `sdk.dir=...` in `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.paynote.app/.ui.MainActivity
```

After allowing PayNote notifications, run the physical-device regression checks with one Android device connected:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The additional manual-entry tests cover validation, corrected-name/category searches and exports, one-currency widget totals, correction preservation after duplicate alerts, widget entry intents, and migration from the previous database version. Test data is synthetic and is removed after device tests.

### Data and permissions

Notification access is needed for automatic logging; notification posting permission enables the purpose prompts. PayNote reads SMS notification text through the notification listener rather than requesting access to the SMS inbox. It does not make payments. CSV export shares a file only through a destination you choose. Android backup is enabled and may include the local database according to your device's backup settings. Build tools, local SDK paths, screenshots, device databases and local QA logs are excluded from this repository.

The device tests verify three separate equal-value receipts into Meezan, NayaPay and JazzCash, simultaneous app/SMS duplicate suppression, app-first and SMS-first delivery order, saving purposes through actual notification reply/category/skip actions, and repairing incomplete JazzCash/SadaPay receipts without changing saved purposes. They use synthetic records and remove only their own test records and notifications. On app startup, known fallback details can be recovered from their original saved notification text when its provider, amount and currency still match the saved transaction. SadaPay's "sender sent you" format is treated as money received. Live Meezan app validation remains pending while the app refuses to open with developer mode enabled; simulator checks do not establish that live path.
