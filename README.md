# SmartStudent Android starter

This is a starter Android app built with Kotlin and Jetpack Compose.

Important limitations:
- AI Chat and exam generation are demo placeholders, not connected to a real AI model.
- Alarms are stored in memory only and do not trigger Android notifications.
- Never put a private AI API key directly in the app; use a secure backend.

Build from GitHub Actions:
1. Add these files to your repository while preserving their exact paths.
2. Commit to main.
3. Open Actions and select the latest Android Build run.
4. When it succeeds, download the SmartStudent-debug-apk artifact and extract the APK.

The workflow installs Gradle 8.7 in the cloud, so the Gradle wrapper files are not required.
