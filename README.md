# Dev Style Studio

Mobile-first React + Capacitor starter for ZIP import, GitHub repository management and Actions build tracking.

## Build APK
1. Upload the contents of this folder to the root of a GitHub repository.
2. Open Actions → **Build Android APK** → **Run workflow**.
3. Download `dev-style-studio-apks` from the completed run's Artifacts section.
4. Extract the artifact ZIP. It includes a debug APK; a signed release APK is included only if signing secrets are configured.

## Optional release signing
Configure repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit the keystore or passwords.

## Included
- Compact stage-based UI for Workspace, Projects, GitHub and Build.
- ZIP import and file tree.
- GitHub token check, repo list/create/delete, binary-safe Git Trees API push.
- GitHub Actions workflow discovery, dispatch, run polling and artifact download.
- Gemini API build-error analysis for failed runs.
- Vercel token verification only; automatic deployment is not implemented.

## Limitations
- Gemini diagnoses errors and suggests fixes; it does not automatically modify code, push a fix, or rebuild.
- Build tracking needs a compatible workflow with `workflow_dispatch` in the selected repository. The included YAML builds this Dev Style Studio project, not an arbitrary uploaded Android project.
- Artifact download may be affected by browser/API permissions; use the run page as a fallback.
- Tokens are stored in WebView localStorage, not encrypted Android Keystore storage. Use least-privilege tokens and revoke them after testing.
