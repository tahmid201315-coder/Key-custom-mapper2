# Mobile Keymapper Pro 🎮⚡

A native Android Gaming HUD & Keymapper Overlay application built in Kotlin. Designed for high-performance touch emulation, customizable floating triggers, draggable target crosshair reticles, and system overlay integration over any Android game or app.

---

## 🌟 Features

- **Draggable Floating HUD Triggers**: Custom responsive on-screen buttons (such as `[A]`, `[B]`, `[X]`, `[Y]`, `[RB]`) that can be repositioned anywhere on the screen.
- **Precision Target Crosshair**: Reticle target markers with live pixel coordinate readouts (`X:###, Y:###`) and pulsing visual feedback.
- **Real Screen Tap Injection**: Powered by a native Android `AccessibilityService` utilizing `dispatchGesture()` for instant synthetic tap simulation without requiring root.
- **System Overlay Engine**: Android `WindowManager` Foreground Service supporting `TYPE_APPLICATION_OVERLAY` for floating over any third-party game or emulator.
- **HUD Control Toolbar**:
  - **Lock/Unlock**: Prevent accidental dragging during active gameplay.
  - **Save Profile**: Store trigger-to-target coordinates.
  - **Dynamic Sizing**: Live slider to scale triggers from 36dp to 96dp.
  - **Opacity Control**: Adjust HUD glass transparency from 20% to 100%.
- **Live In-App HUD Simulator**: Test button positions, coordinate markers, and tap animations directly within the dashboard.

---

## 📱 Tech Stack & Architecture

- **Language**: Kotlin
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 15 / 16 (API 36)
- **UI Framework**: Modern Android XML Views & Material Design 3 Styling
- **Background Architecture**:
  - `KeymapperAccessibilityService`: Gesture dispatch and synthetic touch injection
  - `KeymapperOverlayService`: Floating `WindowManager` overlay service with foreground notification
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`)

---

## 🛠️ Project Structure

```text
├── app/
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── MainActivity.kt                 # Dashboard, permissions & interactive HUD simulator
│   │   │   ├── KeymapperAccessibilityService.kt # Real touch gesture injection engine
│   │   │   └── KeymapperOverlayService.kt      # Floating window overlay service
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── main.xml                    # Dark gaming HUD dashboard layout
│   │   │   │   ├── floating_trigger.xml        # Floating HUD key trigger button
│   │   │   │   ├── floating_target.xml         # Draggable target crosshair reticle
│   │   │   │   └── floating_menu.xml           # Floating HUD control toolbar
│   │   │   ├── drawable/                       # Neon HUD vector graphics & shapes
│   │   │   ├── values/                         # Colors, strings, themes
│   │   │   └── xml/                            # Accessibility service configuration
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml                      # Version catalog dependencies
├── build.gradle.kts                            # Root build configuration
├── settings.gradle.kts                         # Project settings
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio Ladybug / Koala / Hedgehog** (or newer)
- **JDK 17 or JDK 21**
- Android device or emulator running Android 7.0 (API 24) or higher

### Building the Project
1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/mobile-keymapper.git
   cd mobile-keymapper
   ```
2. Open the project in **Android Studio** (`File` > `Open` > select repository folder).
3. Let Gradle sync dependencies.
4. Run on a connected device:
   ```bash
   ./gradlew installDebug
   ```

---

## ⚙️ Permissions Required

1. **Display over other apps (`SYSTEM_ALERT_WINDOW`)**:
   - Required to render the floating triggers, reticles, and toolbars on top of games.
2. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**:
   - Required to inject real taps onto the screen at your target coordinates using `dispatchGesture`.

---

## 📄 License
This project is open source and available under the [MIT License](LICENSE).
