# Project Faro

**Status: initial scaffold — not yet a functional learning application.**

Project Faro is an experimental, privacy-first mathematics learning experience for a seven-year-old child, piloted with an accompanying adult. The first version focuses on manipulable math exercises and short spoken explanations in **Costa Rican Spanish**. This is research-oriented software, **not** a diagnostic tool.

## What we are testing

Can a child solve `18 = 12 + □`, explain how, and receive fair feedback even when speech recognition mishears a number? The MVP must treat an automatic transcript as a hypothesis, never a fact, and must never interpret insufficient evidence as lack of understanding.

The future MVP 0.1 includes three exercise families (missing addend, sequences, decomposed subtraction), visual actions, push-to-talk voice capture, deterministic math checks, conservative assessment with indeterminate states, clarification by speech and images, and a separate adult review view. **None of those features is implemented by this scaffold.**

## Architecture

- `apps/web`: **React 19.3.1 + TypeScript 7.0.2 + Vite 8.3.4**, with separate child/adult features in later issues.
- `services/api`: **Java 27 + Spring Boot 4.1.1 + Gradle 9.8.1**, modular monolith; modules `exercises`, `sessions`, `speech`, `assessment`, `feedback`, and `pilot`.
- `infra/dev`: local run scripts; `infra/whisper`: future local ASR instructions, no model weights.
- `tools`: future reproducible ASR benchmarks.
- `docs`: English architecture, design decisions, research protocol, supply-chain policy, and 41-issue backlog.

Speech recognition and optional explanation interpretation will use **separate application-owned ports and replaceable adapters**. The first runtime candidate is local whisper.cpp; Moonshine and Vosk are benchmark candidates, not prerequisites. The future `LOCAL_ONLY` privacy policy must fail closed with no cloud fallback. No LLM is in the MVP assessment path.

**Compatibility note:** Gradle 9.8.1 supports Java 27, but Spring Boot 4.1.1 currently documents Java compatibility through Java 26 only. CI explicitly tests Java 27; this pairing remains experimental pending officially documented support.

## Prerequisites

- **JDK 27** and **Gradle 9.8.1** installed on `PATH`.
- **Node.js 24 LTS** with **npm 12.2.0**.
- A current desktop browser; no GPU, cloud credentials, microphone, or model download is needed for this scaffold.

## Getting started

```bash
git clone https://github.com/stevdrey/faro.git
cd faro
npm --prefix apps/web ci --ignore-scripts
gradle -p services/api test
npm --prefix apps/web run build
bash infra/dev/start.sh
```

The first `npm ci` requires `apps/web/package-lock.json` to be committed. For development, open **http://127.0.0.1:5173**. The API listens on **http://127.0.0.1:8080**; the Vite server proxies `/actuator` and `/api` to it.

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
# {"status":"UP"}
```

To run separately: `gradle -p services/api bootRun` and `npm --prefix apps/web run dev`.

## Checks

```bash
gradle -p services/api test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
npm --prefix apps/web audit --audit-level=high
npm --prefix apps/web audit signatures
```

## Supply-chain safety

**Dependency lifecycle scripts are disabled:** `apps/web/.npmrc` sets `ignore-scripts=true`. This blocks dependency `preinstall`, `install`, `postinstall` and similar scripts at install time; the CI command repeats `--ignore-scripts`. Deliberate `npm run build` commands still execute (as intended).

We also pin exact direct dependencies, commit a verified `package-lock.json`, use `npm ci`, apply a three-day release age threshold, reject Git/remote/file dependency sources, perform registry signature and vulnerability checks, and pin GitHub Actions to immutable commit SHAs. Gradle dependencies use version locking and verified artifacts. **These are layers of risk reduction, not a guarantee of safety.**

See [Software supply-chain controls](docs/08-supply-chain-security.md) for details, legitimate script exceptions, and branch-protection steps.

## Delivery and backlog

Read the [document index](docs/README.md), [MVP scope](docs/01-vision-and-mvp.md), [architecture](docs/03-architecture-and-evolution.md), [provider independence](docs/05-provider-independence.md), and [backlog](docs/02-backlog-epics-and-issues.md). Proposed ADRs: [ADR-0001](docs/adr/0001-modular-monolith.md), [ADR-0002](docs/adr/0002-provider-independent-models.md).

Eight epics (A–H) organize FARO-001…041. Start with a narrow vertical slice: `18 = 12 + □`, semantic visual actions, a replaceable local ASR, and conservative six-versus-seven clarification. Prefer small, testable pull requests and verification **summaries**, not raw personal evidence.

## Privacy and license

**Do not use this initial scaffold with real children's data or expose it publicly.** The web and API bind to loopback. Recording, adult authorization, consent, deletion, and `LOCAL_ONLY` enforcement are **not yet implemented**. Do not commit recordings, transcripts, personal information, model weights, or secrets.

**License:** Faro-owned source is [GNU AGPL-3.0-or-later](LICENSE). Third-party runtimes, model weights and external assets retain their separate licenses. See [licensing notes](docs/06-license-and-commercial-path.md).
