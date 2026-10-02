# Verdania Launcher

Desktop launcher for installing, verifying, updating, and launching the Verdania Minecraft modpack. Minecraft account authentication and game launch are handled by Prism Launcher.

## Source and builds

Application source: [`launcher/src`](launcher/src). Tests: [`launcher/test`](launcher/test).

Use Node.js 22 on Windows:

```powershell
cd launcher
npm ci
node --test test/profile-installer.test.cjs test/profiles.test.cjs test/launch-gate.test.cjs
npm run dist
```

The Windows installer is written to `launcher/release/`.

GitHub Actions workflow: [Build Verdania Launcher](.github/workflows/build-launcher.yml). It runs tests and builds an unsigned installer on Windows. Run it from **Actions > Build Verdania Launcher > Run workflow**. The installer is available in the run's artifacts. This workflow does not publish a release or request a signature.

## Privacy

See the [privacy policy](PRIVACY.md).

## Signing status

Current builds are unsigned. SignPath Foundation approval and signing integration are not yet configured. This repository does not claim a SignPath signature or sponsorship.

## License

The project's own launcher source code, build scripts, and documentation are licensed under [MIT](LICENSE), copyright 2026 Lolillo (VoodKaRL). Artwork and third-party software retain their own rights and licenses; this permission does not relicense Minecraft, Prism Launcher, modpack dependencies, or artwork. Asset licensing must be reviewed separately for SignPath eligibility.
