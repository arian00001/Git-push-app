# DEV STYLE Studio — Android source starter

- **Package:** `site.devstyle.studio`
- **Language/UI:** Kotlin + Jetpack Compose
- **Minimum Android:** API 26
- **CI:** `.github/workflows/android.yml`

## Source included

- Compose-based white/lime-green Studio UI and bottom navigation.
- Create HTML and React/Vite starter project files.
- Import ZIP projects through Android's document picker, with archive path validation.
- Local project metadata and source-file storage.
- Basic text editor, save, and static HTML WebView preview.
- GitHub token encrypted at rest using Android Keystore AES-GCM.
- GitHub account test, repository listing/creation, project file push, and repository deletion with confirmation.
- GitHub Actions workflow using JDK 17, Gradle 8.9, Android SDK 35, debug APK artifact upload, and test report upload.

## Build workflow notes

This project does not include `gradlew` or the Gradle wrapper JAR. The workflow explicitly installs Gradle 8.9 with `gradle/actions/setup-gradle@v4` and therefore runs `gradle` directly. Do not replace those commands with `./gradlew` unless a valid Gradle wrapper is added.

The workflow is configured for Android Gradle Plugin 8.7.3, which uses Gradle 8.9 and JDK 17. It installs Android SDK Platform 35 and Build Tools 35.0.0.

To build, push the extracted folder contents to a GitHub repository, then open **Actions → Android APK → Run workflow**. Download `dev-style-studio-debug-apk` if a build succeeds. This is a debug APK, not a signed production release.

## Honest feature status

This ZIP is a functional **starter**, not the complete universal IDE from the original PRD. Static HTML preview and basic source editing are implemented. The following remain unimplemented: actual Node.js runtime/React server execution, full Android project build/preview inside the app, AI patch engine, Vercel/Netlify deployment, Gmail inbox and auto-reply, six-hour preview process manager, and full APK release signing automation. These are not represented as working features in the UI.

## Security notes

Never commit API tokens, passwords, signing keys, `.env` files, or private keys. Review all files before pushing. GitHub permissions must be sufficient for the selected repository. GitHub API push currently targets text files and has file/size limits; it is not a full binary-capable Git client.
