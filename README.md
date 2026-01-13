# Uber Time Tracker

A sleek, minimalist Android app for tracking working hours with automatic Uber app detection, multi-language OCR support, and German timesheet exports.

![Uber Time Tracker](screenshots/mockup.png)

## ✨ Features

### 🕐 Timer & Tracking
- **Manual Timer Controls**: Start, Pause, Resume, Stop with intuitive UI
- **Auto-Sync with Uber App**: Automatically detects when you go online/offline in the Uber Driver app
- **Accessibility Service Integration**: Real-time screen content monitoring
- **Foreground Service**: Timer continues running even when app is in background

### 🌍 Multi-Language Detection (OCR + AI)
Automatically detects Uber app language from screen content:
- 🇬🇧 English (EN)
- 🇩🇪 German (DE)
- 🇸🇦 Arabic (AR)
- 🇫🇷 French (FR)
- 🇪🇸 Spanish (ES)
- 🇮🇹 Italian (IT)
- 🇵🇹 Portuguese (PT)
- 🇹🇷 Turkish (TR)
- 🇷🇺 Russian (RU)
- 🇮🇳 Hindi (HI)
- 🇯🇵 Japanese (JA)

### 📊 German Timesheet (ARBEITSZEITLISTE)
- **Auto-calculated dates and days**: Weekdays in German (Mo, Di, Mi, Do, Fr, Sa, So)
- **Monthly calendar view**: März 2026, April 2026, etc.
- **Split shift support**: Start 1, Stop 1, Pause, Gesamtpause, Start 2, Stop 2
- **Weekly totals**: Woche 1 Gesamt, Woche 2 Gesamt, etc.
- **Monthly total**: MONATSGESAMT with hours displayed
- **Weekend highlighting**: Saturday/Sunday rows highlighted in orange
- **Conflict detection**: Visual warning (⚠️) for time overlaps

### 💾 Data Management
- **Offline Cache**: Full offline support with local Room database
- **Cloud Sync**: Ready for Firebase/cloud integration
- **Export Options**:
  - 📊 Excel (XLSX)
  - 📝 Word (DOCX)
  - 📄 PDF

### 🎨 Design
- **Sleek minimalist UI**: Purple branding (#6200EE)
- **Dark & Light modes**: System default or manual selection
- **Material Design 3**: Modern Android design language
- **Smooth animations**: Page transitions, timer pulse, console cursor blink
- **Live Debug Console**: Terminal-style log viewer with color-coded entries

## 📁 Project Structure

```
UberTimeTracker/
├── app/
│   ├── src/main/
│   │   ├── java/com/ubertimetracker/
│   │   │   ├── data/
│   │   │   │   ├── local/           # Room Database, DAOs, Converters
│   │   │   │   ├── model/           # Data models (Session, Pause, Settings)
│   │   │   │   └── repository/      # Session & Settings repositories
│   │   │   ├── di/                  # Hilt dependency injection
│   │   │   ├── receiver/            # Boot receiver
│   │   │   ├── service/             # Accessibility & Timer services
│   │   │   ├── ui/
│   │   │   │   ├── navigation/      # Navigation setup
│   │   │   │   ├── screens/         # Home, Timesheet, Settings screens
│   │   │   │   ├── theme/           # Theme, Colors, Typography
│   │   │   │   └── viewmodel/       # ViewModels with StateFlow
│   │   │   ├── util/                # Export manager utility
│   │   │   ├── MainActivity.kt
│   │   │   └── UberTimeTrackerApp.kt
│   │   └── res/
│   │       ├── drawable/            # Icons
│   │       ├── mipmap-*/            # Launcher icons
│   │       ├── values/              # Strings, Colors, Themes
│   │       ├── values-de/           # German translations
│   │       └── xml/                 # Accessibility config, file paths
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml           # Version catalog
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 🛠️ Tech Stack

- **Language**: Kotlin 1.9
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with StateFlow
- **Dependency Injection**: Hilt
- **Database**: Room
- **Navigation**: Jetpack Navigation Compose
- **OCR**: Google ML Kit Text Recognition
- **Language Detection**: Google ML Kit Language ID
- **Export**: Apache POI (Excel/Word), iText7 (PDF)

## 📋 Requirements

- Android SDK 26+ (Android 8.0 Oreo)
- Target SDK 34 (Android 14)
- Gradle 8.2
- JDK 17

## 🚀 Getting Started

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/UberTimeTracker.git
cd UberTimeTracker
```

2. **Open in Android Studio**
- Open Android Studio
- File → Open → Select the project folder
- Wait for Gradle sync to complete

3. **Build and Run**
```bash
./gradlew assembleDebug
```

4. **Enable Accessibility Service**
- Go to Settings → Accessibility → UberTimeTracker
- Enable the service to allow automatic Uber app detection

## 📱 Screens

### Home Screen (Dark Mode)
- Running timer display (04:32:17)
- Start/Pause/Stop controls
- Auto-Sync toggle
- Status indicators (Offline Cache, Cloud Sync)
- Live Debug Inspector console

### Timesheet Screen (Light Mode)
- German timesheet header (ARBEITSZEITLISTE)
- Month navigation (März 2026)
- Table with all time entries
- Weekend highlighting
- Weekly totals
- Monthly total with export FAB

### Settings Screen
- Accessibility service status
- Sync settings (Auto-Sync, Offline Cache, Cloud Sync)
- Appearance (Theme, Language)
- Notifications
- About section

## 🔑 Permissions

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📞 Support

For support, email support@ubertimetracker.app or open an issue in the repository.

---

**Made with ❤️ for Uber drivers worldwide**
