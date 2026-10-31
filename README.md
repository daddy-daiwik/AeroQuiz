# AeroQuiz ✈️🎯

**AeroQuiz** is a cross-platform real-time 3D flight-simulator serious game built with **Java** and **jMonkeyEngine 3.6 (jME3)** for **Desktop (Linux, macOS, Windows)** and **Mobile (Android)**. Pilots navigate dynamic 3D skies, solving STEM and academic questions by banking their aircraft and firing target projectiles at aerial answer pylon gates while managing engine health, airspeed, and altitude.

---

## 🌟 Key Features

* **3D Flight Simulation & Ballistics**:
  * Dual-mesh aircraft with independent spinning propeller hub.
  * Smoothstep 2D banking maneuvers (`3t² - 2t³`) when targeting aerial obstacle boxes.
  * Precision raycast ballistics: hitting the correct gate triggers target disintegration and bonus score; hitting a wrong gate triggers structural airframe damage.
* **Particle FX & Audio Integration**:
  * Multi-stage progressive damage: light wispy smoke $\rightarrow$ charcoal smoke $\rightarrow$ billowing soot and fire.
  * Camera-facing billboard particles powered by the Kenney Particle Pack.
  * Spatial audio effects: looping engine drone with dynamic pitch shifting, correct/wrong explosion sounds, and low-altitude warnings.
* **Smart Option Layout & Distance Solver**:
  * Supports **2 to 6 options per question** in a balanced 2-row 3D spatial grid.
  * Automatically calculates box dimensions based on option text length with high-visibility solid color backdrops and bold 8-directional text outlines.
  * Dynamic approach velocity calibrated to `timePerQuestion`, ensuring large, readable text and zero pop-in.
* **In-Built Cockpit Quiz & Settings Editor**:
  * **Direct Text Editing**: Edit question prompts and option texts directly from the in-game UI with cursor blinking, live keyboard typing, and dedicated on-screen mobile touch editing buttons (`[ ⌫ DEL ]`, `[ CLEAR ]`, `[ ✔ DONE ]`, `[ ↵ NEXT ]`).
  * **Flight Parameters**: Adjust terrain (**`WATER`**, **`SAND`**, **`SNOW`**, **`GRASS`**), lives (`1` to `5`, `INF`), and question timers on the fly.
  * **Question Management**: Add new questions, delete questions, cycle correct answers, and save changes directly to `questions.txt`, `quiz_settings.txt`, and the local SQLite database.
* **Cockpit UI with Sleek Black Bezel Framing**:
  * Clean, high-contrast dark aesthetic with stylish black outer frames and section containers for flight command terminal and settings editor.
* **Full Mobile & Touch Support**:
  * **Tap-to-Shoot 3D Raycasting**: Tap directly on any floating option box in the sky to bank and shoot.
  * **Touch Option Zones**: Dedicated bottom thumb buttons `[1]` to `[N]` with exact hit boundaries.
  * **Touch Navigation**: One-tap callsign switching, flight take-off, and dedicated touch buttons on Game Over.
* **Pilot Registration Terminal & Leaderboard**:
  * Pre-flight terminal UI with real-time keyboard typing, backspace editing, and a blinking cursor.
  * Plane idles on the runway with spinning propeller while the pilot registers their callsign.
  * Real-time Hall of Fame preview directly on the registration desk.
* **Embedded Java Database Connectivity (JDBC)**:
  * Zero-configuration SQLite engine (`aeroquiz.db`) managed via `QuizDatabase.java` with automatic in-memory fallback for mobile environments.
  * Auto-initializes schema and records pilot callsigns, scores, accuracy, flight duration, and timestamps.
  * Displays the All-Time Top 5 Pilots leaderboard upon flight completion.
* **Dynamic Multi-Terrain & Endless Runway**:
  * Ground terrain switchable via configuration: **`WATER`**, **`SAND`**, **`SNOW`**, or **`GRASS`**.
  * Endless runway algorithm dynamically shifts terrain along $-Z$ to track aircraft forward velocity.
* **Instant Retry System**:
  * Press <kbd>R</kbd> (or tap upper screen) on Game Over for an instant flight retry with the current pilot.
  * Press <kbd>Enter</kbd> / <kbd>Space</kbd> (or tap lower screen) to return to the Pilot Registration Terminal.

---

## 🎮 Controls

| Screen | Desktop Controls | Mobile / Touch Controls | Function |
| :--- | :--- | :--- | :--- |
| **Registration Terminal** | Keyboard (`A-Z`, `0-9`, `_`, `-`, Space) | Tap `< PREV` / `NEXT >` buttons | Cycle pilot callsigns |
| | <kbd>Backspace</kbd> | Tap `CLEAR` button | Delete / reset callsign |
| | <kbd>Enter</kbd> | Tap `[ ► START FLIGHT ◄ ]` button | Confirm callsign & launch flight |
| | | Tap `[ ✏ EDIT QUIZ ]` button | Open in-built Quiz & Settings editor |
| **In-Flight** | Keys <kbd>1</kbd> to <kbd>6</kbd> | **Tap 3D Box in Sky** OR tap bottom thumb buttons `[1]`–`[6]` | Bank aircraft & shoot target option box |
| **Quiz & Settings Editor** | Keyboard typing, <kbd>Backspace</kbd>, <kbd>Enter</kbd> (save field) | Tap Question/Option rows or `[ ✏ EDIT ]` / `[ ✔ DONE ]` | Edit prompt and option strings directly |
| | <kbd>Tab</kbd> / Left / Right arrows | Tap `[ ⌫ DEL ]`, `[ CLEAR ]`, `[ ↵ NEXT ]` buttons | Mobile text editing & field navigation |
| | <kbd>Esc</kbd> | Tap `[ 💾 SAVE ]` or `[ ◄ BACK ]` | Save to disk/database or exit to terminal |
| **Game Over** | <kbd>R</kbd> | Tap `[ ↺ RETRY FLIGHT ]` button | Instant retry flight with current pilot |
| | <kbd>Enter</kbd> / <kbd>Space</kbd> | Tap `[ ⚙ PILOT TERMINAL ]` button | Return to Pilot Registration Terminal |

---

## 📁 Academic Presentation & Dossier Files

Included directly in this project root:
- 📊 **`AeroQuiz_Singleplayer_Presentation.pptx`**: 16:9 Widescreen slide deck with cockpit avionics cards, formulas, and architecture diagrams.
- 🌐 **`AeroQuiz_Presentation.html`**: Interactive presentation slide deck viewable in any browser with keyboard and touch navigation.
- 📄 **`AEROQUIZ_SINGLEPLAYER_REPORT.md`**: Complete academic paper/report detailing pedagogical motivation, aerodynamic equations, collision detection, and performance benchmarks.

---

## 🚀 Building and Running

### Desktop (Linux, macOS, Windows)
```bash
./gradlew :desktop:run
```

### Android APK Build
```bash
./gradlew :android:assembleDebug
```
The output APK is generated at:
`android/build/outputs/apk/debug/android-debug.apk`
