# Call Recorder — Android Research Application

> **Legal Disclaimer**: This application is built **for testing and research purposes only**.
> Call recording laws vary by jurisdiction. You are solely responsible for ensuring compliance
> with all applicable laws. **Do not** use this application to record calls without the knowledge
> or consent of all parties where required by law. This application is not published to the
> Google Play Store and is distributed as a manually-installed APK.

---

## Project Overview

A production-grade Android call recorder supporting:
- GSM / CDMA phone calls
- WhatsApp audio & video call audio
- Facebook Messenger calls
- Telegram calls
- IMO calls
- Other VoIP apps (Viber, Skype, Zoom, Discord, Teams, Google Meet)

**Tech stack**: Kotlin · MVVM · Hilt · Room · Coroutines/Flow · Navigation Component · Material 3 · ExoPlayer · WorkManager

---

## Project Structure

```
app/
├── src/main/
│   ├── kotlin/com/callrecorder/app/
│   │   ├── RecorderApplication.kt          # Hilt application
│   │   ├── AppLogger.kt                    # Logging wrapper
│   │   ├── accessibility/
│   │   │   └── CallMonitorAccessibilityService.kt  # VoIP call detection
│   │   ├── data/
│   │   │   ├── db/                         # Room database, DAO, entities
│   │   │   └── repository/                 # Repository implementation
│   │   ├── di/                             # Hilt modules
│   │   ├── domain/
│   │   │   ├── model/                      # Domain models (CallType, RecordingDomain)
│   │   │   ├── repository/                 # Repository interface
│   │   │   └── usecase/                    # Business logic use cases
│   │   ├── recorder/
│   │   │   ├── AudioRecorderManager.kt     # Strategy orchestrator
│   │   │   └── strategy/                   # Recording back-ends
│   │   │       ├── RecordingStrategy.kt    # Interface
│   │   │       ├── MediaRecorderStrategy.kt # VOICE_CALL / VOICE_DOWNLINK / MIC
│   │   │       ├── AudioPlaybackCaptureStrategy.kt # Android 10+ audio capture
│   │   │       └── MicrophoneStrategy.kt   # Fallback WAV recorder
│   │   ├── receiver/
│   │   │   ├── CallStateReceiver.kt        # GSM call state broadcasts
│   │   │   └── BootReceiver.kt             # Auto-start on boot
│   │   ├── service/
│   │   │   ├── CallRecorderService.kt      # Foreground service for phone calls
│   │   │   └── VoipMonitorService.kt       # Foreground service for VoIP calls
│   │   ├── ui/
│   │   │   ├── MainActivity.kt
│   │   │   ├── dashboard/                  # Stats overview
│   │   │   ├── recordings/                 # List + search + filter
│   │   │   ├── player/                     # Audio playback
│   │   │   ├── settings/                   # App configuration
│   │   │   └── permissions/                # Permission guidance
│   │   └── util/                           # Extensions, FileUtils, NotificationUtils
│   └── res/
│       ├── layout/                         # XML layouts (Material 3)
│       ├── navigation/nav_graph.xml
│       ├── menu/
│       ├── values/                         # strings, colors, themes
│       ├── values-night/                   # Dark theme overrides
│       └── xml/                            # accessibility config, file_paths
```

---

## Architecture

```
UI Layer (Fragments + ViewModels)
        ↓ StateFlow / LiveData
Domain Layer (Use Cases + Repository Interface)
        ↓ suspend functions / Flow
Data Layer (Room DAO + RecordingRepositoryImpl)
        ↓
Service Layer (CallRecorderService + VoipMonitorService)
        ↓
Recorder Layer (AudioRecorderManager + Strategies)
```

All dependencies are injected via **Hilt** — no manual singletons or service locators.

---

## Database Schema

**Table: `recordings`**

| Column             | Type    | Description                          |
|--------------------|---------|--------------------------------------|
| id                 | INTEGER | Primary key (auto-increment)         |
| file_path          | TEXT    | Absolute path to audio file          |
| file_name          | TEXT    | Display filename                     |
| caller_name        | TEXT    | Resolved from contacts (or empty)    |
| phone_number       | TEXT    | Phone number or empty for VoIP       |
| call_type          | TEXT    | PHONE / WHATSAPP / TELEGRAM / …      |
| is_incoming        | INTEGER | 0=outgoing, 1=incoming               |
| timestamp          | INTEGER | Call start time (Unix millis)        |
| duration_ms        | INTEGER | Call duration in milliseconds        |
| file_size_bytes    | INTEGER | File size in bytes                   |
| is_favorite        | INTEGER | 0/1 boolean                          |
| custom_label       | TEXT    | User-set display name                |
| recording_source   | TEXT    | Which strategy was used              |
| created_at         | INTEGER | DB insert time                       |

Indices on: `timestamp`, `call_type`, `phone_number`

---

## Recording Strategies

### Phone Calls (GSM/CDMA)

| Priority | Strategy              | Works On                                |
|----------|-----------------------|-----------------------------------------|
| 1        | VOICE_CALL source     | Most manufacturer ROMs (Samsung, Mi, OPPO…) |
| 2        | VOICE_DOWNLINK source | Incoming audio only on many devices     |
| 3        | VOICE_UPLINK source   | Outgoing audio only                     |
| 4        | VOICE_COMMUNICATION   | VoIP-mode capture                       |
| 5        | MIC fallback          | All devices — your side only            |

### VoIP Calls (WhatsApp, Telegram, etc.)

| Priority | Strategy                    | Works On                       |
|----------|-----------------------------|--------------------------------|
| 1        | AudioPlaybackCapture API    | Android 10+ with MediaProjection |
| 2        | VOICE_COMMUNICATION source  | Some ROMs                      |
| 3        | MIC fallback                | All devices                    |

**VoIP Detection Flow**:
```
AccessibilityService detects VoIP app → foreground
    + AudioManager.mode == MODE_IN_COMMUNICATION
        → Start VoipMonitorService
        → Start recording with best available strategy
        
App leaves foreground OR audio mode changes
        → Stop recording
        → Save metadata to Room database
```

---

## Android API Limitations

### Call Recording Restrictions (Android 9+)

| Android Version | Restriction                                                |
|-----------------|------------------------------------------------------------|
| Android 9 (28)  | `VOICE_CALL` source restricted on **Google stock ROM**     |
| Android 10 (29) | `PROCESS_OUTGOING_CALLS` deprecated; outgoing number via PHONE_STATE extra |
| Android 10 (29) | AudioPlaybackCapture API available for system audio        |
| Android 11 (30) | Background app access to audio mode further restricted     |
| Android 14 (34) | Foreground service type `microphone` required              |
| Android 15 (35) | Additional scoped storage restrictions                     |

**Manufacturer ROMs** (Samsung OneUI, MIUI, ColorOS, OxygenOS, FunTouch) often override
these restrictions and allow VOICE_CALL source recording up to Android 14.

### VoIP Call Recording Limitations

- `AudioPlaybackCapture` only captures audio tagged with `USAGE_MEDIA` or `USAGE_GAME`
- Communication apps typically use `USAGE_VOICE_COMMUNICATION` which is **not capturable** by default
- Without root, the microphone fallback is the most reliable cross-device approach
- Some devices with loudspeaker mode will have speaker audio bleed into mic recording

---

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Step 1: Clone / Extract Project

```cmd
cd d:\Projects\RecorderApp
```

### Step 2: Generate Debug Keystore

```cmd
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey ^
  -keyalg RSA -keysize 2048 -validity 10000 ^
  -storepass android -keypass android ^
  -dname "CN=Android Debug,O=Android,C=US"
```

### Step 3: Build Debug APK

```cmd
gradlew.bat assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`

### Step 4: Build Release APK (signed with debug key for testing)

```cmd
gradlew.bat assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`

### Step 5: Install on Device

```cmd
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or transfer the APK file and install manually (enable "Install from unknown sources").

---

## Post-Install Permission Setup

1. **Basic permissions** — tap "Grant Basic Permissions" on the Permissions screen
2. **Accessibility Service** — required for VoIP detection:
   - Settings → Accessibility → Downloaded apps → Call Monitor → Enable
3. **Display over other apps** — optional for hidden mode:
   - Settings → Apps → Call Recorder → Special app access → Display over other apps
4. **Battery optimization** — exclude app from battery saver:
   - Settings → Battery → Battery optimization → Call Recorder → Don't optimize

---

## Manufacturer-Specific Notes

| Manufacturer | Notes |
|---|---|
| Samsung (OneUI) | VOICE_CALL source works on most OneUI 4/5/6. Some regions have restrictions. |
| Xiaomi (MIUI)   | Enable "All permissions" in MIUI security. VOICE_CALL works up to MIUI 14. |
| OPPO/Realme/OnePlus | ColorOS/OxygenOS allow VOICE_CALL; grant autostart permission in battery settings. |
| Vivo (FunTouch) | Grant autostart + floating window permissions. VOICE_CALL usually works. |
| Poco            | Same as Xiaomi — grant MIUI permissions. |
| Pixel (Stock Android) | VOICE_CALL source **blocked** on Android 10+. MIC fallback only. Use loudspeaker for both-sides capture. |

---

## Device Compatibility Matrix

| Android Version | Phone Recording | VoIP Detection | AudioPlaybackCapture |
|---|---|---|---|
| 8.x (Oreo)      | ✅ VOICE_CALL (most ROMs) | ✅ Accessibility | ❌ Not available |
| 9.x (Pie)       | ⚠️ Manufacturer-dependent | ✅ Accessibility | ❌ Not available |
| 10 (Q)          | ⚠️ MIC fallback on stock  | ✅ Accessibility | ✅ Available (MEDIA usage only) |
| 11 (R)          | ⚠️ MIC fallback on stock  | ✅ Accessibility | ✅ Available |
| 12/12L (S)      | ⚠️ MIC fallback on stock  | ✅ Accessibility | ✅ Available |
| 13 (T)          | ⚠️ MIC fallback on stock  | ✅ Accessibility | ✅ Available |
| 14 (U)          | ⚠️ MIC fallback on stock  | ✅ Accessibility | ✅ Available |
| 15 (V)          | ⚠️ MIC fallback on stock  | ✅ Accessibility | ✅ Available |

---

## Running Tests

```cmd
# Unit tests only
gradlew.bat :app:test

# Test report
start app\build\reports\tests\testDebugUnitTest\index.html
```

---

## Future Extension Points

The modular architecture supports these additions without refactoring:

| Feature | Where to add |
|---|---|
| Cloud sync | New `SyncRepository` + `SyncWorker` in `data/sync/` |
| AI transcription | New `TranscriptionUseCase` calling an ML model or API |
| Contact syncing | Extend `ContactUtils` + add DB join |
| Backup/restore | New `BackupUseCase` exporting Room DB + files |
| Web dashboard | Add a `ktor`/`retrofit` module for backend calls |
| Encryption | Wrap `FileOutputStream` in a `CipherOutputStream` in strategies |
| Waveform display | Add `visualizer` library in `PlayerFragment` |

---

## Building a Signed Release APK (custom keystore)

```cmd
keytool -genkey -v -keystore release.keystore -alias recorder_key ^
  -keyalg RSA -keysize 4096 -validity 36500 ^
  -storepass YOUR_STORE_PASS -keypass YOUR_KEY_PASS

# Update app/build.gradle.kts signingConfigs.release with your keystore details
gradlew.bat assembleRelease
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Recording is silent | VOICE_CALL blocked | Enable loudspeaker; MIC will pick up both sides |
| VoIP calls not detected | Accessibility service not enabled | Enable in Android Settings → Accessibility |
| App killed during call | Battery optimization | Exclude app from battery saver |
| No file saved | Storage permission denied | Grant storage permission or use internal storage |
| Service crashes on boot | AutoStart blocked | Enable AutoStart for app in manufacturer settings |
| Short recordings only | Audio focus taken by another app | Lower priority of competing apps |
| Recording cuts off | Wake lock not held | Ensure WAKE_LOCK permission is granted |
