# Local ASR options for short spoken mathematics (Spanish)

**Status:** candidate comparison based on the 16 October 2026 design source. **No child-speech benchmark has been run**; do not generalize vendor WER values to Costa Rican children.

FARO-014 now supplies a [reproducible synthetic benchmark and opt-in adult guide](../../tools/asr-benchmark/README.md)
and [measured verification](../verification/local-asr-benchmark.md). A separate
matched comparison on 12 authorized adult clips found critical-number agreement
of 8/12 for Whisper base and 12/12 for small, at higher latency/memory for small.
These are strict number scores, not mathematical-meaning or assessment accuracy;
qualitative review still found meaning losses. Only reviewed aggregates are
published; recordings and personal transcripts are excluded. Moonshine/Vosk
remain unmeasured until their reviewed adapters/artifacts are installed.
Dependency [#34](https://github.com/stevdrey/faro/issues/34) remains open, and
this evidence does not authorize a pilot or a provider replacement.

| Candidate | Reference scale | Role | License caution |
|---|---|---|---|
| whisper.cpp multilingual **base** | ~74M parameters / ~142 MiB weight file | Initial reproducible baseline | Runtime MIT; verify model-weights terms independently |
| whisper.cpp tiny / small multilingual | ~39M / ~244M parameters | Speed/accuracy alternatives | Check exact model/version and distribution |
| Moonshine **Spanish Tiny Streaming** | ~34M parameters | First CPU-based challenger | Listed as MIT for streaming model; verify exact artifact |
| Moonshine Spanish Small Streaming | ~123M parameters | Follow-up if Tiny struggles with numbers | Check MIT scope and snapshot |
| Vosk `vosk-model-small-es-0.42` | ~39 MB weight files | Very light local baseline | Listed as Apache-2.0; preserve notices |
| Moonshine legacy Spanish Base | ~58M parameters | **Exclude from commercial distribution without license** | Older Community/noncommercial license |

**Do not compare WER across unrelated datasets**. Vendor-level WER numbers in the source are not a result on the Faro pilot. CPU memory, on-disk size, model parameters, and runtime speed are different measurements.

## Repeatable benchmark protocol (FARO-014 technical baseline; future FARO-038 comparison)

- Obtain explicit adult consent; target 30–60 short voluntary speech explanations, never force recording. An adult-only synthetic/technical corpus is acceptable while consent is absent.
- Normalize inputs consistently. Compare each engine with the **same authorized clips**, human reference transcripts, and marked critical numbers/operators.
- Report critical-number accuracy, change of mathematical meaning, WER, operator/connective errors, false-child-error attribution, p50/p95 latency, CPU/RAM, model checksum and version, denominator, and hardware.
- Do not include child audio in Git, CI, public artifacts, or general logs. Publish only reviewed anonymized summaries.
- Choosing a replacement requires safe assessment outcomes first, then number accuracy, latency, memory, and license fit.

References: [whisper.cpp](https://github.com/ggml-org/whisper.cpp), [Moonshine models](https://moonshine-voice.readthedocs.io/en/latest/models/available-models/), [Moonshine licensing](https://moonshine-voice.readthedocs.io/en/stable/license/), [Vosk models](https://alphacephei.com/vosk/models). Treat external license and model claims as subject to verification at the time of download.
