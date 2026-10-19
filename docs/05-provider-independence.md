# Vendor-independent speech recognition and future interpretation

**Status:** architectural reference for ADR-0002. **MVP 0.1:** `RULES_ONLY`, on-device or local-machine ASR, `LOCAL_ONLY` network/privacy policy; no remote audio fallback.

## Interfaces

```java
public interface SpeechRecognizer {
    SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure;
    AsrCapabilities capabilities();
}

public record SpeechRequest(byte[] pcm16Mono, int sampleRateHz,
                            String languageTag, String correlationId,
                            java.time.Duration maxDuration) {}

public record SpeechHypothesis(String transcript, String languageTag,
                               String engineId, String modelId,
                               String modelVersion, long latencyMs,
                               SpeechStatus status) {}

public enum SpeechStatus { TRANSCRIBED, EMPTY, UNRELIABLE, UNAVAILABLE }

public interface ExplanationInterpreter {
    InterpretationEvidence interpret(ExplanationInput request);
}
```

These are **illustrative contracts, not implemented source files**. A future streaming interface can replace `byte[]` behind the same domain-level boundary. Keep ASR distinct from natural-language explanation interpretation and from deterministic math.

## Implementation policy

- `SpeechRecognizer` adapters: fake for deterministic tests, then local whisper.cpp reference. Moonshine Spanish Tiny Streaming and Vosk small-es are alternative **adapters**, not implicit fallbacks.
- `ExplanationInterpreter`: rules/number parser first. Optional Qwen/Ollama, Claude, Gemini, OpenAI, Grok, Kimi, or other adapters only after separate review; LLM **shadow mode cannot affect child-facing decisions**.
- Business domain and assessment cannot import vendor SDKs or depend on a specific model result schema. Adapters normalize only transport/format, not meaning.
- Model selection is explicit in backend configuration. An unavailable local provider yields `UNAVAILABLE`/`NO_EVALUABLE`, **never** an automatic call to a cloud service.
- Preserve engine, model, revision, effective language, latency, and technical status without logging child audio/transcript.
- License review covers runtime, exact weights, tokenizers, voices, and redistribution rights **individually**.

## Proposed configuration shape (illustrative, not active)

```yaml
faro:
  privacy:
    policy: LOCAL_ONLY
  speech:
    provider: whisper-cpp # future: moonshine | vosk | fake
    language: es-CR
    timeout: 8s
    models:
      whisper-cpp:
        model: base-multilingual
        endpoint: http://127.0.0.1:8085
  explanation:
    mode: RULES_ONLY # future: SHADOW_LLM
    provider: none
```

Provider-specific endpoints are Faro conventions, not vendor API guarantees. Switching from one *engine/runtime* to another still requires its installed adapter, model, configuration, license review, and passing contract tests; it must **not** require rewriting math, assessment, or UI.

## Required tests

- Fake and whisper configuration conform to a common result/error contract; unknown providers fail explicitly.
- ASR mishearing six as seven never penalizes the child.
- Timeouts, empty audio, and unavailable providers yield retry/indeterminate results.
- `LOCAL_ONLY` prevents all cloud fallback even if a remote endpoint is present.
- Future shadow LLM malformed output is rejected and cannot change child-visible feedback.
