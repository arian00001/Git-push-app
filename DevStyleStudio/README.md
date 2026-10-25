# Dev Style Studio — Sequential Project Automation

A compact React + Vite + Capacitor UI designed around one workflow rather than a dashboard full of unrelated modules.

## Workflow
1. Upload a ZIP (compressed archive limit: 500 MB).
2. Extract and review its file list.
3. Select or create a GitHub repository; open or delete the selected repository.
4. Push extracted files with progress feedback.
5. Android/Gradle projects receive a basic GitHub Actions APK workflow when one is not already included. Existing `workflow_dispatch` workflows are detected and triggered.
6. Build status is polled from GitHub Actions; available artifacts can be downloaded.
7. On failure, Gemini can inspect real GitHub Actions logs and relevant source files, generate bounded changes to existing files, push the changes, and trigger a retry. Automatic attempts are capped at three to avoid an uncontrolled API/cost loop.
8. React/Vite projects can be connected to Vercel; static HTML projects can request GitHub Pages publishing.

## Setup
- Open Settings and add a GitHub fine-grained token with access to the target repositories. Contents read/write is required for pushes; repository administration is needed for create/delete; Actions read/write is needed to inspect and dispatch workflows; Pages write may be needed to enable GitHub Pages.
- Add a Gemini API key for AI-assisted build fixes.
- Add a Vercel token and ensure the GitHub integration is connected for Vercel deployment.

## Important limits
- The 500 MB limit applies to the compressed ZIP. GitHub's Git Database API does not accept individual blobs above 100 MB; projects containing larger individual files will fail to push using this client-only method. Very large projects may also encounter browser memory/API rate limits.
- Android projects need a working Gradle wrapper and compatible Android Gradle configuration. The generated workflow runs `assembleDebug`; it cannot guarantee every arbitrary project builds.
- Artifact download is a GitHub Actions artifact ZIP; the APK is inside it unless the workflow uploads the APK directly as a separate downloadable artifact.
- Vercel deployment requires a working GitHub integration. Static GitHub Pages publishing requires an `index.html` at the configured root and repository/account eligibility.
- Gemini fixes are constrained to a small number of existing source/config files and at most three automated attempts. This is not a guarantee of fixing every build.
- Tokens are stored in WebView localStorage in this version and are not encrypted. Use narrowly scoped tokens and revoke them when finished. A production release should move secrets to native encrypted storage/backend proxy.

## Build the app
```bash
npm install
npm run build
npx cap add android
npx cap sync android
```

The included GitHub Actions workflow builds the Dev Style Studio wrapper APK. The workflow injected into an uploaded Gradle Android project builds that user's project separately.
