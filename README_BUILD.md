# JARVIS Android Standalone App - Build Instructions

## Package Configuration
- **Application ID:** com.jarvis.ai
- **Application Name:** JARVIS AI Companion
- **Target SDK:** 34 (Android 14)
- **Min SDK:** 24 (Android 7.0+)
- **Audio Engine:** AndroidX Media3 ExoPlayer + MediaSessionService
- **Speech Engine:** Native SpeechRecognizer (Telugu & English)
- **Alarms:** Android AlarmManager (Exact Alarms & Reboot Recovery)

## How to Build the Fresh APK
### Method 1: Android Studio (Recommended)
1. Open Android Studio.
2. Select **Open** and choose folder: C:\Users\gsrte\OneDrive\Desktop\Projects\AI-jarrven\android-app.
3. Let Gradle sync dependencies.
4. Click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
5. Generated APK will be at: pp/build/outputs/apk/debug/app-debug.apk.
6. Copy that APK to: C:\Users\gsrte\OneDrive\Desktop\Projects\AI-jarrven\apk\JARVIS-AI-debug.apk.

### Method 2: GitHub Actions (Automated Cloud Build)
1. Commit and push this ndroid-app folder to your GitHub repository.
2. The included .github/workflows/build.yml will automatically build the APK.
3. Download JARVIS-AI-debug-apk from the GitHub Actions tab.
