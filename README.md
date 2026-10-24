# Project Faro

**Status: local visual interaction demos with optional browser voice capture — not yet a complete learning application.**

Project Faro is an experimental, privacy-first mathematics learning experience for a seven-year-old child, piloted with an accompanying adult. The first version focuses on manipulable math exercises and short spoken explanations in **Costa Rican Spanish**. This is research-oriented software, **not** a diagnostic tool.

## What we are testing

Can a child solve `18 = 12 + □`, explain how, and receive fair feedback even when speech recognition mishears a number? The MVP must treat an automatic transcript as a hypothesis, never a fact, and must never interpret insufficient evidence as lack of understanding.

The future MVP 0.1 includes three exercise families (missing addend, sequences, decomposed subtraction), visual actions, push-to-talk voice capture, deterministic math checks, conservative assessment with indeterminate states, clarification by speech and images, and a separate adult review view. **The three local visual interaction demos, optional browser voice capture and foundational contracts are implemented; the complete learning flow remains future work.**

## Architecture

- `apps/web`: **React 19.3.1 + TypeScript 7.0.2 + Vite 8.3.4**, with separate child/adult features in later issues.
- `services/api`: **Java 27 + Spring Boot 4.1.1 + Gradle 9.8.1**, modular monolith; modules `exercises`, `sessions`, `speech`, `assessment`, `feedback`, and `pilot`.
- `infra/dev`: local run scripts; `infra/whisper`: standalone Docker CPU ASR baseline, no committed model weights.
- `tools`: future reproducible ASR benchmarks.
- `docs`: English architecture, design decisions, research protocol, supply-chain policy, and 41-issue backlog.

Speech recognition has an **application-owned port, immutable request/result contracts, and a deterministic fake adapter**, selected through the required `faro.speech.provider` (the fake only via the `dev` profile). An unset or absent provider fails startup explicitly without fallback. FARO-012 adds a **default-disabled synthetic-only preview** connecting browser audio to the independently managed [whisper.cpp CPU baseline](infra/whisper/README.md), with a digest-pinned image and checksum-verified multilingual base model. Child-audio integration remains blocked on FARO-040 (`LOCAL_ONLY`, issue #21). Optional explanation interpretation will use a separate port. Moonshine and Vosk are benchmark candidates, not prerequisites. No LLM is in the MVP assessment path. See [provider configuration and contract semantics](docs/05-provider-independence.md).

**Compatibility note:** Gradle 9.8.1 supports Java 27, but Spring Boot 4.1.1 currently documents Java compatibility through Java 26 only. CI explicitly tests Java 27; this pairing remains experimental pending officially documented support.

## Prerequisites

- **JDK 27** and **Gradle 9.8.1** installed on `PATH`.
- **Node.js 24 LTS** with **npm 12.2.0**.
- A current desktop browser; no GPU, cloud credentials, microphone, or model download is needed for the block demo.

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

To run separately: `gradle -p services/api bootRun --args='--spring.profiles.active=dev'` and `npm --prefix apps/web run dev`.

## Checks

```bash
gradle -p services/api test
npm --prefix apps/web run test:unit
npm --prefix apps/web run test:install-browser
npm --prefix apps/web run test:e2e
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
npm --prefix apps/web audit --audit-level=high
npm --prefix apps/web audit signatures
```

## Missing-addend block demo (FARO-005 / issue #15)

The home screen displays `12 + □ = 18` using the shared exercise fixture. Drag a
unit from the reserve into the answer area, or return an answer block to the
reserve. Mouse and touch use Pointer Events; large **+ / −** buttons provide tap
and keyboard alternatives. The known twelve blocks stay fixed. The answer can
contain **0–10** blocks, with corrections allowed throughout exploration.

**Listo** closes the local attempt at any quantity and displays the same neutral
**Terminaste** confirmation. It reveals no correctness or expected answer.
**Intentar de nuevo** clears the attempt and starts at zero. Movements and the
terminal finish event use the existing semantic action contract and remain only
in memory: no attempt endpoint, storage, telemetry or grading is involved. Optional
voice capture is a separate post-completion step described below.
The activity works with the web server alone; it does not require the API.
Refreshing or closing the page also discards the attempt.

The shared local completion controller and FARO-009 spoken guidance are described
below; mathematical evaluation belongs to later engine/assessment issues. This demonstration
does not authorize an unsupervised or public pilot.

Web verification uses Node's built-in test runner for the pure reducer and pinned
Playwright/Chromium for real-browser mouse, keyboard, touch, and completion tests.
Install the locked dependencies with npm 12.2.0 and lifecycle scripts disabled,
then explicitly run `test:install-browser` to provision Chromium and its system
libraries (the latter may require administrator permissions). CI performs this
step explicitly too; no dependency install hook downloads a browser. Test data is
synthetic, and screenshots, video, and traces are disabled by default. Generated
reports/results are ignored. See [FARO-005 verification](docs/verification/missing-addend-screen.md).

## Sequence number-line demo (FARO-006 / issue #23)

Select **Secuencias** above the activity to open `30, 28, □` (later visits
reopen the last chosen example), or select the ascending example `10, 12, □`. Each attempt starts at the last visible term.
Choose a jump size of **1, 2, 5, or 10** (initially 1), then use the left/right
buttons with mouse, touch, Enter, or Space; holding a key records one jump. Size
selection alone records no move.
Every jump remains drawn and listed with its starting position, destination,
direction, and magnitude, including corrections and repeated paths. The number
line widens to include explored positions, including negative values, labeling
ticks on 1/2/5×10ⁿ steps (every unit at ordinary ranges). Arcs share a height
lane unless they overlap. Whenever the line overflows, its labeled region becomes
focusable and scrolls horizontally by touch or keyboard.

**Listo** completes at any position with the same neutral **Terminaste** message.
**Empezar de nuevo** clears both unfinished and completed attempts. Switching
examples or activities, or reloading, also discards the current attempt. The
existing v1 semantic trace records `jumpLeft`/`jumpRight`, their positive `value`,
consecutive steps and UTC timestamps, plus one optional terminal `finishAttempt`.
Only signed 32-bit arithmetic limits block movements. No answer is calculated or
graded, and no API, storage or telemetry is added. Shared spoken guidance and
optional post-completion voice capture are described below.
See [FARO-006 verification](docs/verification/sequence-number-line.md).

## Subtraction tens-and-ones demo (FARO-007 / issue #24)

Select **Restas** to explore `53 − 28 = □`, starting with five tens and three
units. Move one ten or unit between **Quedan** and **Quitaste** by dragging with
mouse/touch or using the labeled buttons with tap, Enter or Space. Each ten is
drawn as a bar of ten cells. Both trays display their actual piece counts and
current total. Available tens can be exchanged manually for ten units and back;
no automatic borrowing occurs. Only source-piece availability limits exploration,
so corrections and removal beyond 28 are allowed.

**Listo** finishes at any state with **Terminaste**, without completing the
equation or grading. **Empezar de nuevo** also clears unfinished attempts.
Switching activity or reloading discards the attempt. Explicit exchanges and
movements use the opt-in [v2 action contract](contracts/actions/v2/README.md),
replayable in Java and TypeScript. Blocks and sequences retain v1.
The activity adds no API, storage, telemetry, model provider or dependencies.
Shared optional post-completion voice capture is described below.
See [FARO-007 verification](docs/verification/subtraction-tens-and-ones.md).

## Explicit attempt completion (FARO-008 / issue #25)

One click or tap on **Listo** explicitly completes the active exercise, including
an attempt with no movements. Blocks, sequences and subtraction all deliver one
deeply frozen semantic trace ending in `finishAttempt`. The application retains
one immutable `CompletedAttempt` containing the `exerciseId` and the original
trace: v1 for blocks/sequences, v2 for subtraction. Each screen reports its own
`exerciseId`, so a trace is never attached to a different exercise, and repeated
delivery cannot overwrite it. Production code does not read the snapshot yet; it
is an integration seam observed only by the browser test harness. Exploration and completion show no correct/incorrect cues; the
confirmation remains **Terminaste**, and further movement is blocked.

The snapshot exists only in memory. Restarting, selecting a different activity
or sequence example, reloading, or closing the page discards it. Selecting the
already active activity/example preserves the attempt. Only the chosen sequence
example is remembered while switching activities within the open page; its
actions are discarded. No history, API request, browser storage,
assessment, telemetry or model integration is added. The optional recording panel
uses a separate transient clip; it does not change the completed snapshot. See
[FARO-008 verification](docs/verification/explicit-attempt-completion.md).

## Replayable spoken guidance (FARO-009 / issue #26)

Blocks, both sequence examples, and subtraction provide **Escuchar guía**
(also replays) and a square **Detener guía** control, with a speaker icon, large touch/keyboard controls and an
optional **Ver instrucciones** transcript. A short prerecorded clip names the
exercise and its controls without giving the answer. After **Listo**, the replay
button offers the same neutral next-action guidance at every result. Restart
buttons share a loop-arrow icon so the clip can identify them without reading.

Playback starts only on request; replay starts at the beginning. Finishing,
restarting, changing activities/examples, hiding the page, or leaving it stops
the current clip. A missing clip or blocked playback offers a short retry message
and leaves visual exploration and completion available. Guidance records no
semantic actions and does not change the completed attempt.

Seven fixed Ogg Opus assets (about 20–37 KB each) are served by the web server
alone. Browsers without Opus support, such as Safari/iOS before 18.4, show the retry
message while visual exploration remains available. The accepted first
version uses offline-generated synthetic Latin American Spanish with Costa Rican
wording; its accent is not a validated Costa Rican voice. It needs a supervised
listening/usability review before pilot use. Guidance makes no microphone request and adds no runtime TTS, cloud service,
new dependency, storage or telemetry. FARO-011 reuses the recording-start clip
and the recording-stop wording in the separate optional flow below. See [FARO-009 verification](docs/verification/spoken-guidance.md)
and [asset provenance and regeneration](apps/web/public/audio/README.md).

## Optional browser voice capture (FARO-011 / issue #12)

After **Listo**, every activity offers **Contame cómo lo hiciste**. An accompanying
adult must check the local confirmation and choose **Habilitar grabación**. This
confirmation lasts only in memory for the open page, including activity changes;
it is not authentication or the documented guardian consent required for a pilot.
Enabling the function does not request microphone access. Only an explicit
**Grabar explicación** activation requests browser permission and starts capture.

Tap/click or use Enter/Space to start and **Detener grabación** to finish.
The microphone indicator, elapsed time and **Cancelar** remain visible while
recording. The app automatically stops at 30 seconds. Stopping retains one clip
in memory; **Escuchar mi grabación**, **Detener reproducción**, **Borrar** and
**Grabar de nuevo** allow local review and replacement. Cancel discards captured
chunks, including while permission or final recording data is pending. A late
permission grant after cancellation immediately releases its stream.

Restarting, changing activity/example, hiding/leaving the page or unmounting
stops capture/playback, releases microphone tracks and discards the clip. Returning
never resumes recording. Reselecting the active activity/example preserves it.
Guides and clip playback share one playback owner; starting capture stops playback
and blocks guides until capture has finished. Microphone, permission or recorder
errors show neutral retry messages and leave the visual activities available.

This uses browser `getUserMedia`/`MediaRecorder` in a secure context such as
loopback. In the ordinary demo, the browser's actual encoding is preserved: there is no PCM conversion,
ASR, audio upload, download control, storage, logging or telemetry. The app releases
Blob references and revokes playback URLs on discard; browser-managed memory is
reclaimed by the browser. No dependency, backend or semantic contract changes are
needed. Automated recording verification uses Chromium with synthetic devices;
Firefox/Safari and physical-device usability remain manual checks. See
[FARO-011 verification](docs/verification/browser-voice-capture.md).

## Synthetic browser-to-ASR preview (FARO-012 / issue #16)

This preview is **off by default**, including in the `dev` profile. Enable it only
for synthetic technical tests, pending the independent FARO-040 backend privacy
gate. The existing recording confirmation does not authorize child-audio upload.

Start the verified local baseline, then run the API and web server separately:

```bash
python3 infra/whisper/baseline.py start
gradle -p services/api bootRun --args='--faro.speech.provider=whisper-cpp --faro.speech.synthetic-test.enabled=true'
VITE_FARO_SYNTHETIC_ASR=true npm --prefix apps/web run dev
```

After recording synthetic audio, confirm its synthetic origin and explicitly
choose **Transcribir prueba**. The browser decodes its native format with Web Audio,
downmixes to mono and normalizes to PCM16 little-endian at 16 kHz, for up to
30 seconds. Unsupported codecs, invalid/oversized audio, unavailable inference
and timeouts offer retry while preserving the completed visual attempt.
The API forwards audio through `SpeechRecognizer`; only the adapter knows the
whisper.cpp WAV/multipart protocol. Results remain **unconfirmed hypotheses**
in optional technical details, without grading or changing the semantic trace.

Neither stopping a recording nor listening sends audio. Cancel/delete/re-record,
restart, activity/example changes, hiding/leaving and unmount invalidate pending
decoding/HTTP and clear the result. Inference already accepted may run until its
bounded deadline. Audio/transcripts are transient, with no application files,
browser storage or telemetry. The baseline's Docker logging stays disabled.
This is not the protected adult review or authorization for a pilot.
See [FARO-012 verification and synthetic smoke test](docs/verification/browser-asr-integration.md).

## Supply-chain safety

**Dependency lifecycle scripts are disabled:** `apps/web/.npmrc` sets `ignore-scripts=true`. This blocks dependency `preinstall`, `install`, `postinstall` and similar scripts at install time; the CI command repeats `--ignore-scripts`. Deliberate `npm run build` commands still execute (as intended).

We also pin exact direct dependencies, commit a verified `package-lock.json`, use `npm ci`, apply a three-day release age threshold, reject Git/remote/file dependency sources, perform registry signature and vulnerability checks, and pin GitHub Actions to immutable commit SHAs. Gradle dependencies use version locking and verified artifacts. **These are layers of risk reduction, not a guarantee of safety.**

See [Software supply-chain controls](docs/08-supply-chain-security.md) for details, legitimate script exceptions, and branch-protection steps.

## Delivery and backlog

Read the [document index](docs/README.md), [MVP scope](docs/01-vision-and-mvp.md), [architecture](docs/03-architecture-and-evolution.md), [provider independence](docs/05-provider-independence.md), and [backlog](docs/02-backlog-epics-and-issues.md). Proposed ADRs: [ADR-0001](docs/adr/0001-modular-monolith.md), [ADR-0002](docs/adr/0002-provider-independent-models.md).

Eight epics (A–H) organize FARO-001…041. Start with a narrow vertical slice: `18 = 12 + □`, semantic visual actions, a replaceable local ASR, and conservative six-versus-seven clarification. Prefer small, testable pull requests and verification **summaries**, not raw personal evidence.

## AI agent PR-size preflight (Claude Code and Codex)

Faro allows **40 changed files maximum** per PR: 0–30 passes; 31–40 warns; 41+ fails. The
[PR size policy](docs/verification/pr-size-guard.md) applies to all source, test,
documentation, configuration, deleted, renamed and generated files. If an Issue
would exceed 40 files, **stop and consult the repository owner** to agree on
two or more logically separated, ordered PRs. Each PR must remain independently
testable, buildable and secure; never remove necessary tests or weaken privacy
or supply-chain protections to satisfy the limit.

**Requirements:** Node.js 24, Git, a checkout with an `origin/main` reference,
and a Claude Code or Codex version that supports project-scoped hooks.

```bash
git fetch origin main
node .agents/scripts/pr-size-preflight.mjs
node --test .agents/scripts/pr-size-preflight.test.mjs
```

The local preflight counts unique committed, staged, unstaged and non-ignored
untracked paths relative to the merge base of `origin/main`; it is an estimate,
not a replacement for GitHub's authoritative `PR file count` workflow check.
For an intentionally stacked PR, fetch its base and use
`PR_PREFLIGHT_BASE=origin/target-branch` or `--base origin/target-branch`.

**Claude Code:** review `.claude/settings.json`, open Faro as a trusted project,
and enable/approve its `PreToolUse` Bash hook. Inspect with `/hooks`.
The hook checks recognizable `git push` and `gh pr create` commands.

**Codex (CLI/desktop):** review `.codex/hooks.json`; enable and trust project
hooks, then inspect with `/hooks` in an interactive Codex CLI session. If
the desktop cannot establish hook trust, use the CLI first and restart the
desktop session. Enterprise settings or older versions may disable hooks.
When unsure, run the manual preflight before publishing.

The hooks are a local early warning/block for recognized shell publication
commands. **GitHub Actions is authoritative only when its `PR file count`
status is required by an Active branch ruleset on `main`.** The
[policy document](docs/verification/pr-size-guard.md) explains this administrator
setup. Documentation: [Claude Code hooks](https://code.claude.com/docs/en/hooks)
and [Codex hooks](https://learn.chatgpt.com/docs/hooks).

## Repository agent skill: GitHub Issue Executive Summary

Project Faro includes the `github-issue-executive-summary` skill for **Codex** and **Claude Code**. The instructions are identical and installed at:

- `.agents/skills/github-issue-executive-summary/SKILL.md` (Codex).
- `.claude/skills/github-issue-executive-summary/SKILL.md` (Claude Code).

After an implementing PR is complete, ask an agent to **prepare an executive summary for the associated GitHub Issue**. The skill produces an accessible English report for non-technical stakeholders, using verified PR/Issue evidence and exact test results. It is **not** a PR approval or merge policy. Publishing a comment is a separate, public write action: **only post to the Issue when explicitly requested**; otherwise provide a draft. If no tests ran or evidence is unavailable, state the limitation rather than fabricate results.

No extra dependencies or setup scripts are required. The CI `agent-skills` check ensures both agent-specific copies remain identical.

## Privacy and license

**Do not use this initial scaffold with real children's data or expose it publicly.** The web and API bind to loopback. Browser-local recording now has an in-memory adult confirmation and clip discard. Strong adult authentication/authorization, documented pilot consent management, persistent-data deletion, and backend `LOCAL_ONLY` enforcement remain **unimplemented**. Do not commit recordings, transcripts, personal information, model weights, or secrets.

**License:** Faro-owned source is [GNU AGPL-3.0-or-later](LICENSE). Third-party runtimes, model weights and external assets retain their separate licenses. See [licensing notes](docs/06-license-and-commercial-path.md).
