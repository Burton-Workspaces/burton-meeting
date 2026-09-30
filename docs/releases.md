# Releases

How versions are cut once automation is already configured. **Local signed build + GitHub Release + F-Droid Pages:** [Local release build and publish](#local-release-build-and-publish) below. **First-time GitHub Actions, permissions, and signing secrets:** [build-automation.md](build-automation.md).

Versioning is **[SemVer 2.0.0](https://semver.org/spec/v2.0.0.html)**. The Gradle `versionName` and `versionCode` both come from [`version.txt`](../version.txt):

```
versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH
```

Tags look like `v1.0.0` (`include-v-in-tag` in `release-please-config.json`).

## Conventional Commits

Merges to `master` **must** use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/). CI rejects other subjects (and pull request titles). Install the local hook with `./scripts/install-git-hooks.sh`. Details: [CONTRIBUTING.md](../CONTRIBUTING.md).

release-please on `master` cuts versions automatically:

| Prefix | Effect |
| --- | --- |
| `feat:` | minor bump (pre-1.0 also uses minor for features; `bump-minor-pre-major` is on) |
| `fix:` | patch |
| `feat!:` / `BREAKING CHANGE:` | major |
| `chore:`, `docs:`, `ci:` | no version bump unless configured otherwise |

The release PR updates `version.txt`, `CHANGELOG.md`, and `.release-please-manifest.json`. Merging it tags `vX.Y.Z` and creates the GitHub Release. The **Release assets** workflow then builds and uploads `burton-meeting-<version>.apk`.

The **Release** workflow uses `GITHUB_TOKEN`. The repository must allow Actions to open PRs:

**Settings → Actions → General → Workflow permissions**
- Read and write permissions
- **Allow GitHub Actions to create and approve pull requests**

Without that checkbox, release-please can push `release-please--branches--master` but the job fails with *GitHub Actions is not permitted to create or approve pull requests*.

The APK pack still runs from that workflow when a release is created, and from tag pushes (`release-assets.yml` uploads with `--clobber`). Squash-merge the release PR if GitHub offers it; a merge commit also works as long as the PR was labeled `autorelease: pending`.

## CI

| Workflow | When | What |
| --- | --- | --- |
| [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | PR and push to `master` | `testDebugUnitTest` |
| [`.github/workflows/conventional-commits.yml`](../.github/workflows/conventional-commits.yml) | PR (including title edits) and push to `master` | Conventional Commit subjects |
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | push to `master` | release-please; if a release was created, pack APK |
| [`.github/workflows/release-assets.yml`](../.github/workflows/release-assets.yml) | tag `v*.*.*`, workflow_call, or `workflow_dispatch` | test, signed `assembleRelease`, upload `burton-meeting-<version>.apk` |

The tag must match `version.txt` (without the `v`). Checkout uses the tag ref. Duplicate uploads use `--clobber`.

SDK setup lives in [`.github/actions/setup-android-ci`](../.github/actions/setup-android-ci/action.yml): Temurin 17, Android SDK `platform-tools`, `local.properties` `sdk.dir`.

These workflows match [burton-sonos-android](https://github.com/Burton-Workspaces/burton-sonos-android): same job graph, secrets, and pack steps. Only the GitHub repository name and APK filename differ.

## Signing

Local and CI signing, including how `KEYSTORE_BASE64` maps to your JKS, is documented in [build-automation.md](build-automation.md).

GitHub repository secrets used by [`.github/workflows/release-assets.yml`](../.github/workflows/release-assets.yml):

| Secret | Local source |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the JKS named in `storeFile` |
| `KEYSTORE_PASSWORD` | `storePassword` |
| `KEY_ALIAS` | `keyAlias` (optional; default `burton`) |
| `KEY_PASSWORD` | `keyPassword` (optional; default store password) |

Never commit `keystore.properties` or the keystore.

## Local release build and publish

Do this from a `burton-meeting` checkout. Optional version arguments **must match** [`version.txt`](../version.txt) (today that is `1.0.0`). Scripts accept `1.0.0`, `v1.0.0`, or no argument (reads `version.txt`).

Do **not** hand-edit `version.txt` to invent a new number. After a `feat:` / `fix:` on `master`, merge the release-please PR so `version.txt` and tag `vX.Y.Z` move together. The **Release-please** job only runs when `github.repository` is `Burton-Workspaces/burton-meeting` (see [`.github/workflows/release.yml`](../.github/workflows/release.yml)), so forks do not open version PRs.

### One-time setup

Skip any step you have already done.

**1. App signing** (same JKS CI uses; see [build-automation.md](build-automation.md))

```bash
cp keystore.properties.example keystore.properties
```

Point `storeFile` at your JKS and fill `storePassword`, `keyAlias`, and `keyPassword`.

**2. GitHub CLI** must be able to write this repo’s Releases:

```bash
gh auth status
```

**3. F-Droid index key** (private machine; not the Pages repo)

Reuse the same `~/fdroid` tree as Burton Sonos if it already exists. Otherwise:

```bash
pipx install fdroidserver
export PATH="$HOME/.local/bin:$PATH"
which fdroid   # $HOME/.local/bin/fdroid — not Debian /usr/bin/fdroid
mkdir -p ~/fdroid && cd ~/fdroid
fdroid init
chmod 0600 config.yml
```

Debian `fdroidserver` 2.2.1 cannot scan this app (`androguard` / `res1 must be zero!`). Details: [fdroid.md](fdroid.md).

In `~/fdroid/config.yml` set `repo_url` to `https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo` and set `repo_name` (for example `Burton Workspaces`). Details: [fdroid.md](fdroid.md).

**4. Pages checkout** — clone [Burton-Workspaces/burton-sonos-fdroid](https://github.com/Burton-Workspaces/burton-sonos-fdroid) **next to** this app (`../burton-sonos-fdroid`). `./scripts/publish-fdroid-pages.sh` uses that path by default.

**5. Env file** (so F-Droid publish is one command):

```bash
cp fdroid-pages.env.example fdroid-pages.env
```

`FDROID_ROOT=$HOME/fdroid` is already in the example.

### Each release

**6. Pack the tree that matches `version.txt`.** After a real bump, check out that tag (or build `master` once the release-please PR is merged). Gradle reads `versionName` from `version.txt` in the tree you assemble.

**7. Signed build and attach to this GitHub repo** (optional; CI also does this on the tag)

```bash
cd /path/to/burton-meeting
./scripts/upload-release-apk.sh
```

That runs `assembleRelease`, copies `burton-meeting-<version>.apk` into the repo root (gitignored), and uploads it to GitHub Release `vX.Y.Z` (`--clobber` if the asset already exists).

**8. Publish to the F-Droid Pages repo**

```bash
./scripts/publish-fdroid-pages.sh
```

That reuses `burton-meeting-<version>.apk` if it is still in the app root, runs `fdroid update`, copies only `repo/` into `../burton-sonos-fdroid/fdroid/repo/`, writes `FINGERPRINT`, and pushes. The catalog still includes Burton Sonos and App Hub packages already in `$FDROID_ROOT/repo/`.

**9. Confirm**

- GitHub Release: `https://github.com/Burton-Workspaces/burton-meeting/releases/tag/v1.0.0`
- F-Droid index: `https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo`
- Fingerprint: `../burton-sonos-fdroid/FINGERPRINT` (also printed by the publish script)

Droidify → **Repositories** → **+**

- Address: `https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo`
- Fingerprint: the 64-character hex from `FINGERPRINT`

Replace `1.0.0` with whatever is in `version.txt` on later versions.

## Manual APK retry (CI)

GitHub Actions → **Release assets** → Run workflow → tag `vX.Y.Z` (must already exist and match `version.txt`). That only attaches the APK to the GitHub Release; it does not update the F-Droid Pages repo. For Pages, still run `./scripts/publish-fdroid-pages.sh`.
