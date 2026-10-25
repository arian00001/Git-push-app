# Dev Style Studio

Landscape-first React + Capacitor Android IDE starter for ZIP import, GitHub repository management and Actions build tracking.

## Landscape Android UI
The Android workflow patches `MainActivity` to `screenOrientation="landscape"`. The web UI has a dedicated short-height landscape layout: compact left icon rail, central workflow canvas, and right application-tools panel. The browser preview also switches to this layout when opened in landscape on a short screen.

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


## Focused automation flow
The app opens to a single guided pipeline: Upload ZIP → inspect extracted files → select/create GitHub repository → push files → trigger an existing GitHub Actions workflow → monitor run time/status → download available artifacts. Settings contain GitHub, Gemini, and Vercel credentials. Gemini currently analyzes failed build logs and suggests fixes; generated fixes are not automatically edited, pushed, or retried in this starter. Review any AI-proposed code changes before applying them.

## Verification status
The ZIP archive can be integrity-checked locally. A full Vite/Android build was not completed in the current environment because package dependencies could not be installed (`vite: not found`). The GitHub Actions workflow should be run on GitHub to verify the Android build and signing setup.
