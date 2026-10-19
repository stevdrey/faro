# Local speech recognition (planned)

FARO-031 creates a vendor-neutral `SpeechRecognizer` interface and fake adapter. FARO-040 enforces `LOCAL_ONLY` and fail-closed configuration. FARO-010/012 then integrate the local whisper.cpp inference process.

Reference: https://github.com/ggml-org/whisper.cpp/blob/master/examples/server/README.md

Do not store model weights, recordings, children’s speech, transcripts, or credentials here. Record each downloaded model's exact identifier, license, checksum, and provenance before testing. Nothing in this directory enables recording or transcription yet.
