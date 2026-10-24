# Faro technical documentation

All project documentation and GitHub work items are written in English. The user-facing child experience remains **Spanish (Costa Rica)** because that is the pilot's target language.

| Document | Purpose |
|---|---|
| [Vision and MVP 0.1](01-vision-and-mvp.md) | Research question, scope, exercises, and success criteria |
| [Epic and issue backlog](02-backlog-epics-and-issues.md) | Epic A–H, FARO-001 to FARO-041, priorities, and dependencies |
| [Architecture and evolution](03-architecture-and-evolution.md) | Modules, API ideas, boundary rules, and delivery sequence |
| [Exercise contract v1](../contracts/exercises/v1/README.md) | Public exercise types, backend solution boundary, JSON fixtures, and serialization tests |
| [Assessment and evidence](04-assessment-and-evidence.md) | Types, rules, required regression scenarios |
| [Assessment result contract v1](../contracts/assessments/v1/README.md) | Independent math/evidence/next-step states, minimal provenance, strict JSON binding, and shared fixtures |
| [Provider-independent models](05-provider-independence.md) | ASR ports, configuration, local-only policy, optional future LLM |
| [ASR research](research/asr-alternatives-es.md) | Baseline, candidates, benchmark methodology, license cautions |
| [Local whisper.cpp baseline](../infra/whisper/README.md) | FARO-010 standalone CPU server, pinned artifacts, synthetic Spanish smoke test, licenses and cleanup |
| [Local whisper.cpp verification](verification/local-whisper-baseline.md) | FARO-010 offline tests and real local inference results |
| [Licensing and commercial evolution](06-license-and-commercial-path.md) | AGPL-3.0-or-later for Faro code, external dependencies, SaaS proposals |
| [Pilot, privacy, and accessibility](07-pilot-privacy-and-accessibility.md) | Consent, research metrics, safety, and deletion |
| [Sequence number-line verification](verification/sequence-number-line.md) | FARO-006 behavior, trace boundaries, and reproducible checks |
| [Subtraction tens-and-ones verification](verification/subtraction-tens-and-ones.md) | FARO-007 manipulation, v2 exchange events, compatibility, and checks |
| [Explicit attempt completion verification](verification/explicit-attempt-completion.md) | FARO-008 shared in-memory snapshot, explicit finish, lifecycle, and checks |
| [Browser voice capture verification](verification/browser-voice-capture.md) | FARO-011 explicit consent, transient clips, lifecycle cleanup and synthetic browser checks |
| [Browser ASR integration verification](verification/browser-asr-integration.md) | FARO-012 synthetic preview, normalization, local adapter, retries and real smoke test |
| [Spoken guidance verification](verification/spoken-guidance.md) | FARO-009 prerecorded clips, replay/stop, failure handling, privacy, and checks |
| [Action contract v2](../contracts/actions/v2/README.md) | Explicit decena/unit exchanges and replay of available/removed pieces |
| [Supply-chain security](08-supply-chain-security.md) | npm install-script lockdown, Gradle verification, secure CI and reviews |
| [ADR-0001](adr/0001-modular-monolith.md) | Modular monolith and separately managed local ASR |
| [ADR-0002](adr/0002-provider-independent-models.md) | Own AI capability ports and fail-closed local policy |

| [GitHub Issue Executive Summary skill](../.agents/skills/github-issue-executive-summary/SKILL.md) | Repo-local skill for stakeholder summaries on GitHub Issues (Codex/Claude Code) |

Source basis: *Proyecto Faro — integral kickoff document*, proposed v0.1, 16 October 2026. Architecture decisions and some provider compatibility assumptions still require confirmation; choosing Java 27 does not imply Spring Boot's published support extends to Java 27.
