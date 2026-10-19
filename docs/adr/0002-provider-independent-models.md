# ADR-0002 — Provider-independent ASR and optional language models

- **Status:** Proposed
- **Date:** 2026-10-16
- **Scope:** speech recognition, bounded explanation evidence, privacy

## Context

Faro needs to compare whisper.cpp, Moonshine, Vosk, and potentially later local or hosted LLMs without vendor lock-in. Children's voice recordings require particularly conservative privacy and evaluation safeguards.

## Decision

Define separate first-party ports named `SpeechRecognizer` and `ExplanationInterpreter` with typed inputs, technical failures, and versioned result schema. Place vendor SDKs, audio codec constraints, model format, and inference transport **only in adapters**. `MathEngine` and `EvidenceAggregator` remain vendor-agnostic and deterministically validate any extracted mathematical claims.

Start with a fake ASR adapter for tests and local whisper.cpp baseline. Select configured providers explicitly, validate capability and license assumptions, and fail closed when unavailable. The initial backend-enforced policy is **`LOCAL_ONLY` with no automatic cloud fallback**. Keep the LLM interpreter disabled in MVP 0.1 (`RULES_ONLY`); a later **shadow-only** experiment cannot alter child feedback or replace math rules.

## Consequences

- Positive: reproducible comparisons, clearer privacy review, local CPU use, and future engine swaps without changing assessment/learning business logic.
- Cost: each runtime needs its own adapter, model deployment, error handling, model/version/license inventory, and contract tests.
- Constraint: 'switch by config' means a **supported and installed adapter**; it is not a promise that any arbitrary model can run without new adapter code.

## Rejected alternatives

- Direct SDK imports in assessment or domain code.
- One universal `AIProvider` blending speech with mathematical interpretation.
- Automatic local-to-cloud fallback on child audio.
- LLM as the final judge of the child's comprehension or as a freeform feedback generator.

## Validation

FARO-031/040 define ports and fail-closed policy; contract tests must exercise fake/local provider swaps and engine downtime. FARO-014/038 compare exact-number recognition and false-error attribution before replacing a baseline.

## References

- [Provider-independent design](../05-provider-independence.md)
- [Pilot and privacy](../07-pilot-privacy-and-accessibility.md)
