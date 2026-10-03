# Burton GroupMe

A GroupMe client for Android with the same look as the other Burton apps. Sign in with **Connect with GroupMe**, then read groups and DMs, send messages, like posts, and search.

Signed APKs are published on [GitHub Releases](https://github.com/Burton-Workspaces/burton-groupme/releases). Droidify / F-Droid: [burton-sonos-fdroid](https://github.com/Burton-Workspaces/burton-sonos-fdroid) (`https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo`).

## What it does

- **Home** — groups and direct messages
- **Group** — history, send, likes
- **Search** — groups, DMs, and messages already loaded on the phone
- **Settings** — account, sign out, app version

The token stays on the phone (DataStore). The app talks to GroupMe’s v3 API over HTTPS; there is no Burton cloud account.

## Requirements

- Android 8.0+ (API 26)
- Internet
- A GroupMe account you can authorize (see [Using the app](docs/using.md))

## Docs

| Doc | Contents |
| --- | --- |
| [Using the app](docs/using.md) | Connect with GroupMe, screens |
| [Architecture](docs/architecture.md) | Packages, GroupMe API, caching, polling |
| [Development](docs/development.md) | Build, run, test, project layout |
| [Build automation](docs/build-automation.md) | GitHub Actions, workflow permissions, signing secrets |
| [Releases](docs/releases.md) | SemVer 2.0, local build + publish walkthrough, GitHub Releases |
| [F-Droid / Droidify](docs/fdroid.md) | Same catalog as Burton Sonos, Fingerprint, setup + Pages publish |
| [Contributing](CONTRIBUTING.md) | Conventional Commits (required) |

## Quick start (debug)

```bash
./gradlew :app:installDebug
```

Debug builds use application id `com.burton.groupme.debug`. Release builds need a keystore; see [docs/releases.md](docs/releases.md).

```bash
./gradlew testDebugUnitTest
```

One-time F-Droid and GitHub signing setup:

```bash
./scripts/setup-fdroid-and-secrets.sh
```

## License and scope

This is a household GroupMe client. It does not replace the official GroupMe app for polls, calendar events, or account administration.
