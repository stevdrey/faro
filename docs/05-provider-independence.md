# Vendor-independent speech recognition and future interpretation

**Status:** ASR contracts, fake adapter and configuration selection implemented by FARO-031; real ASR integration and FARO-040 privacy enforcement remain future work. **MVP 0.1 target:** `RULES_ONLY`, on-device or local-machine ASR, `LOCAL_ONLY` network/privacy policy; no remote audio fallback.

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

public record AsrCapabilities(java.util.Set<Integer> sampleRatesHz,
                              java.util.Set<String> languageTags,
                              java.time.Duration maxAudioDuration,
                              boolean localProcessing) {}

public record ProviderConfig(String provider, String language,
                             java.time.Duration timeout) {}
```

These signatures summarize the implemented pure Java contracts in `io.github.stevdrey.faro.speech`; constructor validation and privacy protections are omitted from the snippets. A future streaming interface can replace `byte[]` behind the same domain-level boundary. Keep ASR distinct from natural-language explanation interpretation and from deterministic math. `ExplanationInterpreter` remains a future port, not implemented here.

### Audio and result semantics

- Requests carry transient signed PCM16 **little-endian mono** samples. Empty audio is valid. Sample rate and maximum audio duration must be positive; incomplete samples and audio exceeding the declared duration are rejected. The byte array is copied on construction and access. `maxDuration` bounds audio length; `ProviderConfig.timeout` is the separate inference deadline future adapters must enforce.
- Capabilities contain immutable, nonempty sets of positive rates and exact language tags, maximum audio duration and a local-processing declaration. `supports(request)` requires a supported rate/language and a requested duration bound within the adapter's maximum; language aliases and fallback are not inferred. The declaration alone does **not** enforce `LOCAL_ONLY`.
- Results preserve transcript text exactly, effective language, engine, model, revision and nonnegative latency. `TRANSCRIBED` requires nonblank text; `EMPTY` and `UNAVAILABLE` require empty text. `UNRELIABLE` can retain an uncertain transcript or have none. There are no segments or confidence fields yet.
- Adapter operational failures throw `SpeechFailure` with `UNAVAILABLE`, `TIMEOUT`, `INCOMPATIBLE_REQUEST` or `PROVIDER_ERROR`. `UNAVAILABLE` in `SpeechStatus` is reserved for a future application-level conversion of failures; adapters use the exception channel. No assessment conversion is implemented here.
- Request/result string representations redact sensitive content and metadata. Failure messages contain only the stable code; vendor exception causes and suppressed exceptions cannot be attached. These contracts provide no storage or logging. Future adapters must not log or persist audio/transcripts, and must normalize vendor errors without copying raw messages.

### Fake adapter and registration

The production fake is deliberately non-recognizing: compatible empty or nonempty audio returns an `EMPTY` hypothesis with `fake` / `synthetic` / `1` provenance and zero latency. It advertises 16 kHz, `es-CR`, at most 30 seconds, and local processing. Tests can construct it with a synthetic `TRANSCRIBED`/`EMPTY`/`UNRELIABLE` hypothesis or a failure code, without clocks, network, processes or model weights. It never retains input requests.

Installed adapters register an `AsrProvider` Spring bean with a unique `id()` and `create(ProviderConfig)` factory. `SpeechConfiguration` validates all registration IDs, invokes **only** the explicitly selected factory, verifies the configured language is supported and exposes one `SpeechRecognizer` bean. Unknown or absent providers, duplicate IDs, unsupported language and invalid configuration fail startup. A selected factory failure is propagated; no alternate factory is invoked. The pure port and data objects do not import Spring or vendor SDKs.

There is **no production whisper.cpp adapter** in this issue. Selection of an installed `whisper-cpp` factory is exercised with a test-only recognizer, not a real runtime integration. Supporting a real adapter remains separate work.

## Implementation policy

- `SpeechRecognizer` adapters: fake for deterministic tests, then local whisper.cpp reference. Moonshine Spanish Tiny Streaming and Vosk small-es are alternative **adapters**, not implicit fallbacks.
- `ExplanationInterpreter`: rules/number parser first. Optional Qwen/Ollama, Claude, Gemini, OpenAI, Grok, Kimi, or other adapters only after separate review; LLM **shadow mode cannot affect child-facing decisions**.
- Business domain and assessment cannot import vendor SDKs or depend on a specific model result schema. Adapters normalize only transport/format, not meaning.
- Model selection is explicit in backend configuration. An unavailable local provider raises a technical failure; future consumers should translate it to unavailable/non-evaluable feedback, **never** automatically call a cloud service.
- Preserve engine, model, revision, effective language, latency, and technical status without logging child audio/transcript.
- License review covers runtime, exact weights, tokenizers, voices, and redistribution rights **individually**.

## Active configuration

The following defaults are active in `application.yaml` and in configuration binding. Properties can be overridden through normal Spring Boot configuration, for example `FARO_SPEECH_PROVIDER` or `--faro.speech.timeout=3s`. Provider/language must be nonblank and timeout must be positive. The fake currently supports only `es-CR`.

```yaml
faro:
  speech:
    provider: fake
    language: es-CR
    timeout: 8s
```

Choosing `whisper-cpp` today fails explicitly because its adapter is not installed. No audio endpoint is exposed. This selection mechanism is **not** the FARO-040 backend network safety gate.

## Future configuration extensions (illustrative, not active)

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

## Verification and future integration tests

- Implemented: defensive audio copying, duration bounds, capability immutability, result provenance, redacted representations and safe failure codes.
- Implemented: deterministic fake results/failures, configuration binding, test-only installed-whisper selection, unknown/unavailable provider and duplicate-registration failures without fallback.
- Future integration: real local adapter conformance, inference deadline enforcement and empty/unavailable results translated to retry/indeterminate feedback.
- FARO-040: `LOCAL_ONLY` blocks remote execution even if a remote endpoint is configured.
- Future assessment/shadow work: six-versus-seven misrecognition never penalizes the child; malformed shadow LLM output cannot change child-visible feedback.
