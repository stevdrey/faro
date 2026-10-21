# Faro technical documentation

All project documentation and GitHub work items are written in English. The user-facing child experience remains **Spanish (Costa Rica)** because that is the pilot's target language.

| Document | Purpose |
|---|---|
| [Vision and MVP 0.1](01-vision-and-mvp.md) | Research question, scope, exercises, and success criteria |
| [Epic and issue backlog](02-backlog-epics-and-issues.md) | Epic A–H, FARO-001 to FARO-041, priorities, and dependencies |
| [Architecture and evolution](03-architecture-and-evolution.md) | Modules, API ideas, boundary rules, and delivery sequence |
| [Exercise contract v1](../contracts/exercises/v1/README.md) | Public exercise types, backend solution boundary, JSON fixtures, and serialization tests |
| [Assessment and evidence](04-assessment-and-evidence.md) | Types, rules, required regression scenarios |
| [Provider-independent models](05-provider-independence.md) | ASR ports, configuration, local-only policy, optional future LLM |
| [ASR research](research/asr-alternatives-es.md) | Baseline, candidates, benchmark methodology, license cautions |
| [Licensing and commercial evolution](06-license-and-commercial-path.md) | AGPL-3.0-or-later for Faro code, external dependencies, SaaS proposals |
| [Pilot, privacy, and accessibility](07-pilot-privacy-and-accessibility.md) | Consent, research metrics, safety, and deletion |
| [Supply-chain security](08-supply-chain-security.md) | npm install-script lockdown, Gradle verification, secure CI and reviews |
| [ADR-0001](adr/0001-modular-monolith.md) | Modular monolith and separately managed local ASR |
| [ADR-0002](adr/0002-provider-independent-models.md) | Own AI capability ports and fail-closed local policy |

| [GitHub Issue Executive Summary skill](../.agents/skills/github-issue-executive-summary/SKILL.md) | Repo-local skill for stakeholder summaries on GitHub Issues (Codex/Claude Code) |

Source basis: *Proyecto Faro — integral kickoff document*, proposed v0.1, 16 October 2026. Architecture decisions and some provider compatibility assumptions still require confirmation; choosing Java 27 does not imply Spring Boot's published support extends to Java 27.
