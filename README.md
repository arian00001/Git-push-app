# Dev Style Studio

A mobile-first React + Capacitor starter for importing a ZIP project and managing GitHub repositories from Android.

## Included
- Reference-inspired lime/glassmorphism interface, responsive for phone and desktop.
- ZIP import and local file-tree preview (JSZip).
- GitHub connection check, repository list, create/delete repo, and Git Trees API push.
- Vercel token verification.
- GitHub Actions workflow to build a debug APK artifact.

## Build APK with GitHub Actions
1. Create a new GitHub repository and upload these project files to its root.
2. Open **Actions** and enable workflows if prompted.
3. Open **Build Android APK** → **Run workflow**. A push to `main`/`master` also triggers it.
4. When the run completes, open its **Artifacts** section and download `dev-style-studio-debug-apk`.
5. Extract the artifact ZIP; install `app-debug.apk` on your Android phone.

The workflow creates the Capacitor Android project during CI. This repository intentionally does not include generated Gradle files.

## Run locally
- Node.js 20+
- `npm install`
- `npm run dev` for browser preview.
- To generate Android locally: `npm run build && npx cap add android && npx cap sync android`, then build `android/app` with Android Studio/Gradle.

## GitHub token permissions
Use a fine-grained personal access token limited to the repositories you need. Grant **Contents: Read and write** for push. Repository creation/deletion requires **Administration: Read and write** and broader account access. If you only need push, do not grant administration.

## Important limitations / security
- This is a working starter, not a certified production release. Review and test before relying on it.
- GitHub file push uses the Git Trees API and is intended for normal text-based source projects. Binary assets (images, fonts, archives) are not safely supported by the current text conversion and may be corrupted. Do not use it to push binary files until binary-safe blob upload is added.
- The Vercel integration currently verifies a token; automated project creation/deployment is not implemented yet.
- AI scanner, email center, full code editing, SAF-native file picker and pull/merge UI are placeholders, not implemented backend features.
- Credentials are stored in WebView `localStorage`, not Android Keystore encrypted storage. Use a dedicated least-privilege token, never upload the settings store, and revoke tokens after testing. For a production build, replace with native encrypted storage and add an explicit biometric/lock option.
- Never paste access tokens into chat, screenshots, public repositories, or issue reports.
