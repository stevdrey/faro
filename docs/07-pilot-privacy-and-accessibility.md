# Family pilot, safety, privacy and accessibility

**Population:** one supervised child, around seven years old. This is exploratory family research, **not** medical, clinical, or educational diagnosis.

## Privacy first

- Before any real voice work: documented guardian consent, explicit recording activation, visible/audible recording state, cancel/stop affordance, and no persistent listening.
- Process audio **locally** and erase temporary copies after transcription even on errors. Any retained research sample is an adult opt-in with expiry and verifiable deletion.
- Use random/pseudonymous session identifiers; do not require name, school, birthday, or persistent identity.
- No cloud upload or silent fallback in MVP 0.1; block it in the **backend**, not just the browser.
- Store only minimal action traces, state versions, technical ASR metadata, and required review evidence. Avoid raw text/audio in general logs; never automatically train on child data.
- Provide controlled adult-only review, annotation, export and deletion. **Do not expose to a LAN or Internet until strong adult authentication/authorization exists.**

## Pilot protocol

1. Adult-only technical test for microphone permissions, capture, audio errors, and safe fallbacks.
2. Baseline of **8–12** problems without app assistance, noting help level.
3. **3–5** optional short sessions (roughly **8–12 minutes**, adjusted to interest and fatigue). Stop if the child is uncomfortable.
4. Transfer assessment of **8–12 new** but conceptually related problems under the same help protocol.
5. Aim for **30–60 short explanations** only if comfortable and consented. Adult compares original speech reference, ASR hypothesis, actions, and assessment result.

## Pilot metrics

- All-critical-numbers-correct / reviewed clips; WER, operation substitutions and omissions separately.
- **False errors attributed to the child** / audited attempts — primary safety signal. Aim for **zero observed severe false errors**; a small sample never proves a true zero rate.
- Adult-assessment agreement, indeterminate share, number of clarifications/retries, abandonment/friction.
- End-of-recording to feedback latency (median and p95), CPU/RAM and operational cost observations.
- Performance on **new** problems before/after with help annotated, and voluntary participation.

## Accessibility and non-punitive interaction

Large touch targets, keyboard alternatives, labeled controls, contrasting visuals, short voice guidance, and re-playable instructions. The child should never need to read an ASR transcript to resolve an ambiguity; show two visual alternatives or allow retry/skip. Audio cannot be the only method of participation in future versions.

## Decision to advance

Pause automated grading if a serious false-attribution case is observed; record root cause. Decide whether to improve capture/ASR, alter clarification, progress to a more varied alpha, or stop based on **audited evidence**, not apparent enjoyment alone. One child's results do not generalize.
