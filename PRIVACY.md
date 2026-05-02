# Privacy Policy — Local Skills

This is a working draft for the Play Store privacy policy. Replace the contact details before publishing.

## Summary

Local Skills processes everything on-device. Your text, images, and the structured data it extracts never leave your phone unless you explicitly share them through the Android share sheet.

## Data we collect

**None that leaves your device.**

The app stores the following on your phone, in app-specific internal storage protected by Android's per-app sandbox:

- Skills you install or author (manifests).
- Inputs you give a skill: pasted text, an image you picked, or text you shared into the app.
- Structured outputs the app extracts from your inputs.
- Reminders you attach to a skill, and a record of which reminders have already fired.
- Your edits and corrections, used to improve future runs of the same skill on your device.

We do not transmit any of the above to any server we control. We do not run analytics, ad SDKs, or crash reporters that exfiltrate user content.

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Posts a local notification when a rule you set up fires (e.g. "coupon expires in 3 days"). |
| `RECEIVE_BOOT_COMPLETED` | Lets WorkManager re-register the daily rule sweep after a reboot, so reminders keep working. |

We deliberately do **not** request:

- SMS / Call Log access.
- Notification listener / accessibility access.
- Background location.
- Contacts.
- Always-on microphone or camera.

The camera and image picker are only invoked in response to a user action (you tap "Pick image" inside a skill).

## Sharing

When you tap **Share** on a skill, the app builds a plain-text or JSON payload and hands it to the system share sheet. From that point the destination app (e.g. WhatsApp, Drive, Mail) is responsible for the data — read its privacy policy.

Imported skills are inspected by an on-device linter and sandbox before they are saved. They are always installed **disabled**, so you must explicitly review and turn one on before it can run.

## Backup

Cloud backup and device-to-device transfer are disabled in `AndroidManifest.xml`. Your skills, reminders, and saved results stay on the device they were created on.

## Children

Local Skills is not directed at children under 13. The app has no online services, accounts, or social features.

## Changes

If a future version of the app starts collecting any data that leaves your device, this policy and the Play Store Data Safety section will be updated before that version ships.

## Contact

`<your contact email>`
