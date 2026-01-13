# Uber Time Tracker - Changes Summary

## Changes Made

### 1. ✅ Multi-Language Uber Eats Detection
**File:** `util/UberEatsLanguageDetector.kt`

Added support for detecting Online/Offline status in **20+ languages**:
- 🇬🇧 English
- 🇩🇪 German (Deutsch)
- 🇸🇦 Arabic (العربية)
- 🇷🇺 Russian (Русский)
- 🇫🇷 French (Français)
- 🇪🇸 Spanish (Español)
- 🇮🇹 Italian (Italiano)
- 🇵🇹 Portuguese (Português)
- 🇹🇷 Turkish (Türkçe)
- 🇮🇳 Hindi (हिन्दी)
- 🇯🇵 Japanese (日本語)
- 🇨🇳 Chinese Simplified (简体中文)
- 🇹🇼 Chinese Traditional (繁體中文)
- 🇰🇷 Korean (한국어)
- 🇳🇱 Dutch (Nederlands)
- 🇵🇱 Polish (Polski)
- 🇺🇦 Ukrainian (Українська)
- 🇹🇭 Thai (ไทย)
- 🇻🇳 Vietnamese (Tiếng Việt)
- 🇮🇩 Indonesian (Bahasa Indonesia)

### 2. ✅ File Naming Format
**File:** `util/ExportManager.kt`

File names now follow the format: `Arbeitszeitliste_YYYY_MM.{pdf|xlsx|docx}`

Examples:
- `Arbeitszeitliste_2026_01.pdf`
- `Arbeitszeitliste_2026_02.xlsx`
- `Arbeitszeitliste_2026_03.docx`

### 3. ✅ Removed "Running in Background" Notification Text
**File:** `service/TimerService.kt`

Notification now only shows:
- Title: "Uber Zeiterfassung"
- Content: "Sitzung läuft: HH:MM:SS"

No more "running in background" text.

### 4. ✅ Live Debug Inspector - Only Online/Offline Status
**File:** `ui/screens/HomeScreen.kt`

The Live Debug Inspector now only shows:
- 🟢 **Online** - When Uber Eats driver is online
- 🔴 **Offline** - When Uber Eats driver is offline
- ⚪ **Unknown** - When status cannot be determined

All other debug messages have been removed.

### 5. ✅ Timesheet with Month Name and Total at Bottom Right
**File:** `ui/screens/TimesheetScreen.kt`

- Shows German month names (Januar, Februar, März, April, Mai, Juni, Juli, August, September, Oktober, November, Dezember)
- **MONATSGESAMT** (monthly total) displayed at bottom right
- Weekly totals: "Woche 1 Gesamt", "Woche 2 Gesamt", etc.
- Weekend highlighting (Saturday/Sunday in orange)
- Conflict detection with ⚠️ warning icon

### 6. ✅ Removed "11:02" Column from Table
**File:** `ui/screens/TimesheetScreen.kt`

Table columns now:
| Datum | Tag | Start 1 | Stop 1 | Pause | Gesamtpause | Start 2 | Stop 2 | Gesamt |

---

## Files Created

```
app/src/main/java/com/ubertimetracker/
├── data/
│   ├── local/
│   │   └── Database.kt          # Room DB, DAOs, Converters
│   ├── model/
│   │   └── DataModels.kt        # Session, Pause, Settings
│   └── repository/
│       ├── SessionRepository.kt
│       └── SettingsRepository.kt
├── di/
│   └── AppModule.kt             # Hilt DI module
├── service/
│   ├── TimerService.kt          # Timer with clean notification
│   └── UberAccessibilityService.kt  # Multi-language detection
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt        # With updated debug inspector
│   │   └── TimesheetScreen.kt   # German timesheet
│   └── viewmodel/
│       ├── HomeViewModel.kt
│       └── TimesheetViewModel.kt
└── util/
    ├── ExportManager.kt         # PDF/Excel/Word export
    └── UberEatsLanguageDetector.kt  # Multi-language detection
```
