# ADR-0001 — Modular monolith for MVP 0.1

- **Status:** Proposed
- **Date:** 2026-10-16
- **Scope:** initial architecture

## Context

The first family pilot must run locally at low cost, expose reproducible pedagogical decisions, and leave room for commercial evolution without reimplementing deterministic mathematics.

## Decision

Use a small **monorepo** with a TypeScript/Vite browser UI and one **Java/Spring Boot modular monolith** organized by capabilities. Keep mathematical checks pure and deterministic, session traces semantic, and private pilot evidence locally stored behind a storage boundary. Run computationally expensive ASR as a separately managed local process accessed through a port. The first ASR baseline will be whisper.cpp, not a dependency of the mathematical domain.

**Out of the MVP critical path:** Phaser, mandatory LLM interpretation, cloud infrastructure, payments, microservices, background speech listening and hosted accounts.

## Considered alternatives

- **Microservice per concern:** rejected for now due to deployment, observability, and privacy complexity at one-child scale.
- **Everything in the browser:** not selected because robust local ASR/runtime support varies across devices.
- **LLM-centric orchestration or grading:** rejected because math/strategy evidence needs reproducibility and explicit uncertainty.
- **Game-engine-first app:** deferred until visual interaction and learning hypotheses are tested.

## Consequences

- Positive: small PRs, fast tests, lower operational complexity, and independently replaceable UI, storage, and ASR edges.
- Cost: explicit module contracts and integration tests are needed from early development; a later hosted version needs new operational and security safeguards.

## Review triggers

Revisit when measured workload or security/isolation constraints justify a service boundary, or module dependency tests show repeated architectural friction. Do not extract services purely by convention.

## References

- [Architecture](../03-architecture-and-evolution.md)
- [Backlog](../02-backlog-epics-and-issues.md)
