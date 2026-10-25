# DEV STYLE Studio — Android source (GitHub-enabled starter)

**Package:** `site.devstyle.studio`  
**Language/UI:** Kotlin + Jetpack Compose  
**Minimum Android:** API 26 (Android 8.0)  
**Build:** GitHub Actions (`.github/workflows/android.yml`)

## Implemented in this ZIP

- White + lime-green Compose UI and five-item bottom navigation.
- Create workspaces for HTML, React/Vite starter files, Android/Gradle notes, and general source.
- Import ZIP through Android's Storage Access Framework.
- ZIP entry count limit and canonical-path validation against path traversal.
- Project metadata and source files persisted in app-private storage.
- Basic text editor, file explorer, and save.
- Static `index.html` WebView preview (JavaScript disabled for safer initial preview).
- GitHub token save encrypted using Android Keystore AES-GCM.
- Test GitHub authentication, list repositories, create public/private repositories, select a repository, push project files through GitHub's Git Data API, and delete a selected repository after confirmation.
- GitHub push excludes common generated directories and limits a push to 1,000 files / 15 MB total; binary files are rejected.
- GitHub Actions workflow installs JDK 17, Gradle 8.9 and Android SDK 35, then builds and tests the debug variant and uploads an APK artifact.

## Important status / limitations

This is an improved starter, **not the complete 35-capability IDE described in the original specification**. It has not been compiled in this environment because Android SDK/Gradle are unavailable here. Run the included GitHub Actions workflow and inspect its logs before treating the build as verified.

The GitHub integration is real API code, but it requires a user-provided token with appropriate repository permissions. A fine-grained token must have access to the target repositories and contents read/write permissions; repository creation/deletion also needs the corresponding account/repository administration permissions. A classic token commonly needs the `repo` scope. Use the minimum permissions required.

### GitHub push behavior

1. Open Settings → GitHub Access.
2. Paste a GitHub Personal Access Token and tap Save token. The field clears after saving.
3. Tap Test, then Refresh under Repositories.
4. Select an existing repository, or enter a name and tap Create.
5. Select the imported project, set a commit message, then tap Review & Push Project.
6. Check the status message for the number of pushed files and commit short SHA.
7. Delete is destructive: select a repository, tap Delete selected repository, and confirm only if you intend to permanently delete it.

ZIP upload means: import/extract the ZIP into a Studio project, then push that project's text files to the selected repository. The original ZIP file itself is not uploaded as a single archive. Large/binary assets are rejected by the current GitHub push adapter rather than silently corrupted. Review project contents before pushing. Never put secrets or `.env` files into the project; the push code does not yet implement comprehensive secret scanning.

## Build with GitHub Actions

1. Create a repository on GitHub.
2. Extract this ZIP and upload/push the **contents** of the extracted folder, not just the ZIP file.
3. Open Actions and run **Android APK** (or push to `main`).
4. Download the `dev-style-studio-debug-apk` artifact from the completed workflow run.
5. Extract the artifact ZIP to obtain the debug APK.

The debug APK is not a signed production release.

## Not yet implemented

- Real Node.js runtime / actual React or Vite dev server.
- Native Android Gradle build or full Android emulator inside the app.
- Syntax-highlighted editor and robust diff/review UI.
- AI provider and patch engine.
- Vercel/Netlify deployment.
- Gmail sync and automatic replies.
- Full runtime download manager and six-hour preview lifecycle.
- Complete secret scanning and project export.

## Security

- No GitHub token is embedded in source or APK. The token is encrypted at rest with an Android Keystore AES-GCM key.
- GitHub network calls use HTTPS and API requests have timeouts.
- Imported ZIP paths are validated before extraction.
- WebView disables JavaScript, file access and content access in this version.
- Never commit API keys, email app passwords, signing keys, or `.env` files.
