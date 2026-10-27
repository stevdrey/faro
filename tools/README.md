# Research tools

[ASR benchmark](asr-benchmark/README.md) implements FARO-014's synthetic-only
baseline measurement, with critical-number scoring, WER, latency and container
resources, plus a separate in-memory own-voice guide for consenting adults.
Measurement uses the pinned local Whisper baseline without downloads.
An explicit setup command can install verified small weights for a matched
base/small comparison. The own-voice guide also has an opt-in 24-hour private
WAV checkpoint for recovery after interruption; ordinary use remains memory-only.
Moonshine/Vosk adapters and an annotated multi-engine A/B comparison remain
FARO-036/037/038.

Never put children's recordings or identifiable transcripts in Git or CI.
Publish only reviewed, de-identified metrics and methodology.
