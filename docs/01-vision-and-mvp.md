# Vision and MVP 0.1

**Status:** proposal for a one-child family research pilot; **target language:** Spanish (Costa Rica). This is not a clinical or diagnostic application.

## Research question

> Can we offer an enjoyable experience in which a child explains a mathematical solution aloud, speech recognition is reliable enough, and errors introduced by the system are not unfairly attributed to the child?

Insufficient evidence **must not** be interpreted as lack of understanding. The pilot is intentionally limited and cannot establish population-level effectiveness.

## Pilot users

- **Child (around seven):** uses large visual/touch controls and brief spoken guidance, with limited reading requirements.
- **Accompanying adult:** starts and supervises sessions; reviews ambiguous cases using a separate adult interface.
- **Developer/researcher:** studies number recognition, assessment reliability, latency, and friction using minimal pseudonymous records.

## In scope

1. Browser-local learning UI with accessible large controls, brief prerecorded guidance, and one pseudonymous pilot profile (no child login).
2. Three deterministic exercise families: missing addend (`18 = 12 + □`), arithmetic sequences (`30, 28, □`), and subtraction by tens and units (`53 − 28`).
3. Visual blocks and number-line interactions, semantic action trace, and explicit **I'm done** confirmation (never grade during exploratory moves).
4. Short, **push-to-talk** speech capture, local replaceable ASR with whisper.cpp as first baseline, and structured transcription metadata.
5. Deterministic math engine and conservative rules-based strategy interpretation. Disambiguate misheard numbers through spoken prompts and two visual alternatives.
6. Separate protected adult review view, human reference annotations, metrics, export/deletion, and a consent-based pilot procedure.

## Out of scope

- Complete game / Phaser adventure, animated characters, accounts, SaaS multitenancy, payment, advertising, social features, English localization.
- Generating exercises or freeform grades with an LLM, real-time conversational voice, autonomous agents, cloud audio handling, or persistent background listening.
- Diagnoses, causal learning-improvement claims, emotion inference, and automatic model training on children's audio.

## Example interactions

- The child places **six** blocks for `18 = 12 + □`, saying they added six to twelve: a correct action and coherent spoken strategy **if evidence is sufficiently clear**.
- The child places six, but ASR returns **seven**: flag `CONFLICTO_ASR` (ASR conflict), never penalize; show six and seven pictorially and ask what was said.
- The child places six but says only 'because': mathematical action can be correct while strategy evidence stays insufficient.
- The child decomposes `53 − 28` into removing 20 and then 8 but says `33 − 8 = 26`: valid decomposing idea with a distinct possible arithmetic mistake, **only if the verbal evidence is unequivocal**.

## Phases

0. Define baseline contracts and verify technical feasibility.
1. Implement functional MVP 0.1 with the three visual exercises, speech, assessment, and clarification.
2. Conduct short guided family sessions and audit false attribution of errors.
3. Optionally benchmark further local ASR engines and a **shadow-only** LLM.
4. Only with evidence, prepare invited-family alpha, authorization, privacy, and broader accessibility.
5. Consider commercial product, games, and SaaS **only if evidence justifies investment**.

## MVP definition of done

- All three exercise families usable without reading transcripts; short oral explanation supported.
- Math/action and strategy/evidence states are separate; ASR six-versus-seven cannot cause an incorrect academic judgment.
- Automated regression coverage, replayable reason codes, adult audit, local voice handling, and deletion capability.
- Pilot protocol performed with recorded limitations and an evidence-based go/no-go report.

**Current implementation:** only basic web/API scaffolding is present; all criteria above are future work.
