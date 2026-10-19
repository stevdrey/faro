# Project Faro

**Status: initial scaffold — not yet a functional learning application.**

Project Faro is an experimental, privacy-first mathematics learning experience for a seven-year-old child, piloted with an accompanying adult. The first version focuses on manipulable math exercises and short spoken explanations in **Costa Rican Spanish**. This is research-oriented software, **not** a diagnostic tool.

## What we are testing

Can a child solve `18 = 12 + □`, explain how, and receive fair feedback even when speech recognition mishears a number? The MVP must treat an automatic transcript as a hypothesis, never a fact, and must never interpret insufficient evidence as lack of understanding.

The future MVP 0.1 includes three exercise families (missing addend, sequences, decomposed subtraction), visual actions, push-to-talk voice capture, deterministic math checks, conservative assessment with indeterminate states, clarification by speech and images, and a separate adult review view. **None of those features is implemented by this scaffold.**

## Architecture

- `apps/web`: Vite + TypeScript browser application; eventual child and adult features.
- `services/api`: Java 21 + Spring Boot 3.5 modular monolith; module boundaries: `exercises`, `sessions`, `speech`, `assessment`, `feedback`, and `pilot`.
- `infra/dev`: local run scripts; `infra/whisper`: future local ASR instructions (no model weights).
- `tools`: future reproducible ASR benchmarks.
- `docs`: English architecture, design decisions, research protocol, and 41-issue backlog.

Speech recognition and optional explanation interpretation will use **separate application-owned ports and replaceable adapters**. The first runtime candidate is local whisper.cpp; Moonshine and Vosk are benchmark candidates, not prerequisites. The future `LOCAL_ONLY` privacy policy must fail closed with no cloud fallback. No LLM is in the MVP assessment path.

## Prerequisites

- JDK 21 (or newer JDK capable of compiling Java 21 code).
- Gradle **8.14.3** installed on `PATH` (Gradle Wrapper is a follow-up; no wrapper JAR is committed).
- Node.js **22+** and npm.
- A current desktop browser; no GPU, cloud credentials, microphone, or model download is needed for this scaffold.

## Getting started

```bash
git clone https://github.com/stevdrey/faro.git
cd faro
npm --prefix apps/web install
gradle -p services/api test
npm --prefix apps/web run build
bash infra/dev/start.sh
```

Open **http://127.0.0.1:5173**. The development API listens on **http://127.0.0.1:8080**; the Vite dev server proxies `/actuator` and `/api` to it.

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
# {"status":"UP"}
```

To run processes separately: `gradle -p services/api bootRun` and `npm --prefix apps/web run dev`. Stop the combined script with Ctrl+C.

## Checks

```bash
gradle -p services/api test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
```

GitHub Actions runs these build checks **without speech recordings or model weights**. The domain, UI workflow, and ASR contract tests are separate future issues.

## Delivery and backlog

Read [the document index](docs/README.md), [MVP scope](docs/01-vision-and-mvp.md), [modular architecture](docs/03-architecture-and-evolution.md), [provider independence](docs/05-provider-independence.md), and [the complete backlog](docs/02-backlog-epics-and-issues.md). The two initial proposed ADRs are [ADR-0001](docs/adr/0001-modular-monolith.md) and [ADR-0002](docs/adr/0002-provider-independent-models.md).

Eight epics (A–H) organize FARO-001 through FARO-041. Start with a narrow vertical slice: `18 = 12 + □`, semantically recorded visual actions, a swappable local ASR interface, and a conservative six-versus-seven clarification. Prefer small, testable pull requests and attach **summaries** of verification, not raw personal evidence.

## Privacy and license

**Do not use this initial scaffold with real children's data or expose it to a network.** The web and backend bind to loopback only. Recording, adult authorization, consent, deletion, and the `LOCAL_ONLY` enforcement are **not yet implemented**. Do not commit recordings, transcripts, personally identifying information, third-party model weights, or secrets. Tests should use synthetic cases.

**License:** Faro-owned source code is licensed under [GNU AGPL-3.0-or-later](LICENSE) (version 3 of the GNU Affero General Public License, or any later version at the recipient's option). Third-party runtimes, model weights, recordings, instructional media, and other external assets keep their own licenses and require separate review. See [licensing notes](docs/06-license-and-commercial-path.md).
