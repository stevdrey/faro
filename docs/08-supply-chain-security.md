# Software supply-chain controls

**Status:** enforceable baseline; no configuration eliminates all risk. These restrictions cover installation-time execution, dependency intake, CI permissions, and reproducibility. Runtime/build-time code from approved packages still executes while building the app.

## Node/npm (web)

Use **Node 24**, **npm 12.2.0**, **React 19.3.1**, **TypeScript 7.0.2**, **Vite 8.3.4**, and **@vitejs/plugin-react 6.1.2**. Exact versions are intentional. Review each upgrade in a PR.

`apps/web/.npmrc` sets:

- **`ignore-scripts=true`**: suppresses dependency `preinstall`, `install`, `postinstall`, and related install-time lifecycle scripts, including a dependency's malicious postinstall payload. This also blocks legitimate native setup scripts. Avoid blanket exceptions; review any required one-off build process carefully.
- **`min-release-age=3`**: avoid installing versions published within three days when resolving a new dependency tree. A critical patch may require a carefully reviewed and documented exception. This is **not** a malware detector.
- **`allow-git=none`, `allow-remote=none`, `allow-directory=none`, `allow-file=none`**: prevent unreviewed Git, remote URL, directory, or file-based dependency sources under npm 12.
- **`engine-strict=true`**, **`save-exact=true`**, **`strict-peer-deps=true`** and native integrity checks in the committed lockfile.

Install with `npm --prefix apps/web ci --ignore-scripts`, **never `npm install` in CI**. Run explicit project-owned scripts with `npm --prefix apps/web run build`; npm intentionally allows requested scripts even when `ignore-scripts` is true. Avoid uncontrolled `npx` and arbitrary `npm exec` commands. Signature and vulnerability checks: `npm --prefix apps/web audit --audit-level=high` and `npm --prefix apps/web audit signatures`.

**Important:** no install script does **not** mean no malicious code execution: a compromised dependency can run at build time, when imported by Vite, or in the browser. Keep review, lockfile integrity, minimal dependency count, and independent security checks.

## Standalone ASR baseline

FARO-010's [local whisper.cpp tooling](../infra/whisper/README.md) uses a reviewed
official CPU image pinned by digest and a multilingual model pinned by immutable
repository revision, byte length and SHA-256. The committed artifact manifest
records provenance, license sources and intended use independently for runtime
and weights. Setup is explicit; startup refuses missing/corrupt weights and uses
`--pull=never`. No model/voice downloads occur in CI or dependency install hooks.
Review manifest updates and rerun real local inference; the upstream image's OS,
libraries and bundled utilities keep their separate licenses. FARO-041 remains
responsible for the general artifact inventory.

The benchmark's [small-model manifest](../tools/asr-benchmark/small-artifact.json)
likewise pins its converted-model revision, HTTPS download, byte length, SHA-256,
LFS metadata source and independent MIT weight license. Explicit setup alone can
download it; base and small start with the same cached digest-pinned CPU image,
separate local containers/ports and no runtime downloads or provider fallback.

## Java/Gradle

Use Java 27 and Gradle 9.8.1; 9.8.1 addresses important security issues from the initial 9.8 release. Spring Boot 4.1.1 is the current stable baseline, but its official documented Java compatibility currently extends through Java 26: **Java 27 is a test-required compatibility risk until Spring confirms support**.

Pin Gradle plugins. Resolve dependencies only through approved repositories, enable dependency locking and SHA-256 verification metadata. Do not use `mavenLocal()`, arbitrary Maven URLs, or dynamic versions. Changes to Gradle lockfiles or checksums must be reviewed.

## GitHub Actions / review

- Read-only `GITHUB_TOKEN` by default; CI does not accept child recordings, tokens or third-party model weights.
- Pin GitHub Actions to **full immutable commit SHAs** (not mutable major tags).
- Validate both API and UI, `npm audit`, signatures, and PR dependency review.
- Enforce review before updating dependency manifests/lockfiles, Gradle verification metadata, or workflow definitions. Configure repository branch rules: require checks, at least one review, dismiss stale approvals, restrict force-pushes and direct writes. Add CODEOWNERS reviewers if available.
- Do not permit an untrusted PR to run under `pull_request_target` with repository secrets.
- **Repository owner action required:** turn on **Dependency graph** (and Dependabot alerts) in [GitHub Code security and analysis](https://github.com/stevdrey/faro/settings/security_analysis). The Dependency Review PR job fails closed while GitHub's dependency graph is disabled; do not skip that check or treat it as successful.

## Updates and security incidents

Update direct dependencies in their own small PR. Review upstream publication history, maintainer changes, package scripts, integrity hashes, transitive diffs, and advisories. Stop an affected build if a compromise is suspected; do not rely solely on `npm audit`.

**JavaScript runtime behavior:** `ignore-scripts` is specifically an installation-time mitigation, not a sandbox. Use GitHub Actions isolation and least-privilege build environments.
