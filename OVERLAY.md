# Floating Overlay (call protection bubble)

Branch: `feature/overlay`

## What it does
- When a call rings or starts, a floating bubble appears over the phone's call screen
- The user picks a monitoring session: 15 / 30 / 60 min
- The bubble shows a countdown. 2 min before the end it asks "Extend?"
- The session ends automatically when the call hangs up
- Bubble colour = risk level: grey (off), green (safe), yellow (caution), red (scam)

## Files
| File | Job |
|---|---|
| `OverlaySetupActivity.java` | Asks for permissions, turns protection on/off |
| `CallWatcherService.java` | Background service; detects call start/end |
| `CallSessionTimer.java` | Session logic: start / extend / end + countdown |
| `OverlayManager.java` | Draws the bubble + panel over other apps |
| `Risk.java` | Risk levels + colours |
| `res/layout/overlay_*.xml`, `res/drawable/*_bg.xml` | Bubble + panel UI |
| `AndroidManifest.xml` | Added 5 permissions, `OverlaySetupActivity`, `CallWatcherService` |

## How to connect it (TODO when the main app is ready)
1. **Open the setup screen from the app** (e.g. a button in `DashboardActivity`):
   ```java
   startActivity(new Intent(this, OverlaySetupActivity.class));
   ```
2. **LLM → bubble warning.** When the scam model returns a result during a session, call:
   ```java
   overlay.showWarning(Risk.DANGER, "Asked for gift cards + urgency");
   ```
   `overlay` lives in `CallWatcherService`. Easiest hookup: start the speech-to-text + LLM loop
   in `CallWatcherService` inside `onSessionStarted`, and stop it in `onSessionEnded`.
3. **Only monitor during a session.** Audio/transcription should run only while
   `session.isActive()` is true (privacy: the user controls when monitoring happens).
4. **Microphone in the background.** When recording is added, add
   `FOREGROUND_SERVICE_MICROPHONE` permission and change the service type to
   `specialUse|microphone` in the manifest.
5. **Messaging apps (WhatsApp, Messages, etc.).** Later: an `AccessibilityService` that detects
   those apps and calls `overlay.showPrompt()`, same as a call starting.
6. **Check for overlap** with the existing `SessionManager.java` and merge if they do the same thing.

## How to test on its own
1. Run the app on a real phone (emulators can't take real calls)
2. Open `OverlaySetupActivity`, tap **Turn on call protection**, grant permissions
3. Call the phone from another phone: the bubble should appear, count down, and disappear on hang-up
