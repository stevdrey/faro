# Vendor-independent speech recognition and future interpretation

**Status:** ASR contracts, fake adapter and configuration selection implemented by FARO-031; backend `LOCAL_ONLY` admission and fail-closed checks implemented by FARO-040. Real ASR integration remains future work. **MVP 0.1 target:** `RULES_ONLY`, on-device or local-machine ASR, `LOCAL_ONLY` network/privacy policy; no remote audio fallback.

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
                             java.time.Duration timeout, java.net.URI endpoint) {}
```

These signatures summarize the implemented pure Java contracts in `io.github.stevdrey.faro.speech`; constructor validation and privacy protections are omitted from the snippets. A future streaming interface can replace `byte[]` behind the same domain-level boundary. Keep ASR distinct from natural-language explanation interpretation and from deterministic math. `ExplanationInterpreter` remains a future port, not implemented here.

### Audio and result semantics

- Requests carry transient signed PCM16 **little-endian mono** samples. Empty audio is valid. Sample rate and maximum audio duration must be positive; incomplete samples and audio exceeding the declared duration are rejected. The byte array is copied on construction and access, and requests have value equality over the audio content. `maxDuration` is the caller's bound; `audioDuration()` is the actual sample length. `ProviderConfig.timeout` is the separate inference deadline.
- Capabilities contain immutable, nonempty sets of positive rates and language tags, maximum audio duration and a local-processing declaration. All language tags (requests, results, capabilities, configuration) must be well-formed BCP 47 and are normalized to canonical case, so `es-cr` equals `es-CR`; ill-formed tags are rejected, and aliases or fallback are not inferred. `supports(request)` requires a supported rate/language and actual audio length within the adapter's maximum, regardless of the caller's declared bound. FARO-040 checks the declaration at startup and before each guarded transcription, in addition to pre-factory provider and endpoint validation.
- Results preserve transcript text exactly, effective language, engine, model, revision and nonnegative latency. `TRANSCRIBED` requires nonblank text; `EMPTY` and `UNAVAILABLE` require blank text (whitespace-only engine output for silence is preserved, not rejected). `UNRELIABLE` can retain an uncertain transcript or have none. There are no segments or confidence fields yet.
- Adapter operational failures throw `SpeechFailure` with `UNAVAILABLE`, `TIMEOUT`, `INCOMPATIBLE_REQUEST` or `PROVIDER_ERROR`. `UNAVAILABLE` in `SpeechStatus` is reserved for a future application-level conversion of failures; adapters use the exception channel. No assessment conversion is implemented here.
- Request/result string representations redact sensitive content and metadata. Failure messages contain only the stable code; vendor exception causes and suppressed exceptions cannot be attached. These contracts provide no storage or logging. Future adapters must not log or persist audio/transcripts, and must normalize vendor errors without copying raw messages.

### Fake adapter and registration

The production fake is deliberately non-recognizing: compatible empty or nonempty audio returns an `EMPTY` hypothesis with `fake` / `synthetic` / `1` provenance and zero latency. It advertises 16 kHz, `es-CR`, at most 30 seconds, and local processing. Tests can construct it with a synthetic `TRANSCRIBED`/`EMPTY`/`UNRELIABLE` hypothesis or a failure code, without clocks, network, processes or model weights. It never retains input requests.

Installed adapters register an `AsrProvider` Spring bean with a unique `id()`, an explicit `localProcessing()` declaration and a `create(ProviderConfig)` factory. Local processing defaults to false, so existing or undeclared adapters are denied until reviewed and explicitly opted in. Bean constructors and registration must be free of I/O; initialization belongs in the selected factory after admission. `SpeechConfiguration` validates all registration IDs, invokes **only** the explicitly selected factory, verifies the configured language is supported and exposes one guarded `SpeechRecognizer` bean. The guard enforces, once for every adapter, the adapter's capabilities, the configured language and the inference timeout (`TIMEOUT`, with the adapter call interrupted); unchecked adapter exceptions and null results become `PROVIDER_ERROR` without attaching the vendor exception. Unknown or absent providers, duplicate IDs, unsupported language and invalid configuration fail startup with messages naming the provider id or language (configuration values, never child data). A selected factory's `SpeechFailure` is propagated and unchecked factory exceptions are replaced by `PROVIDER_ERROR` without their message; no alternate factory is invoked. The pure port and data objects do not import Spring or vendor SDKs.

There is **no production whisper.cpp adapter** in this issue. Selection of an installed `whisper-cpp` factory is exercised with a test-only recognizer, not a real runtime integration. Supporting a real adapter remains separate work.

## Implementation policy

- `SpeechRecognizer` adapters: fake for deterministic tests, then local whisper.cpp reference. Moonshine Spanish Tiny Streaming and Vosk small-es are alternative **adapters**, not implicit fallbacks.
- `ExplanationInterpreter`: rules/number parser first. Optional Qwen/Ollama, Claude, Gemini, OpenAI, Grok, Kimi, or other adapters only after separate review; LLM **shadow mode cannot affect child-facing decisions**.
- Business domain and assessment cannot import vendor SDKs or depend on a specific model result schema. Adapters normalize only transport/format, not meaning.
- Model selection is explicit in backend configuration. An unavailable local provider raises a technical failure; future consumers should translate it to unavailable/non-evaluable feedback, **never** automatically call a cloud service.
- Preserve engine, model, revision, effective language, latency, and technical status without logging child audio/transcript.
- License review covers runtime, exact weights, tokenizers, voices, and redistribution rights **individually**.

## Active configuration

`faro.speech.provider` has **no default**: startup fails until it is set, so a deployment can never silently run the non-recognizing fake. The fake is selected only by the `dev` profile (`application-dev.yaml`) and by the test-only `config/application.yaml` overlay. `language` (`es-CR`) and `timeout` (`8s`) keep binding defaults. `faro.privacy.policy` defaults to `LOCAL_ONLY`; every other value (including blank) fails startup before factory invocation. There is no cloud opt-in. Optional `faro.speech.endpoint` is validated before any factory call and passed as a URI in `ProviderConfig`; absence means no configured service transport. The three-argument `ProviderConfig` constructor remains available for in-process adapters. Properties can be overridden through normal Spring Boot configuration, for example `FARO_SPEECH_PROVIDER` or `--faro.speech.timeout=3s`. Provider/language must be nonblank and timeout must be positive. Run locally with `--spring.profiles.active=dev`. The fake currently supports only `es-CR`.

```yaml
# application.yaml (provider intentionally absent)
faro:
  privacy:
    policy: LOCAL_ONLY
  speech:
    language: es-CR
    timeout: 8s
---
# application-dev.yaml
faro:
  speech:
    provider: fake
```

Choosing `whisper-cpp` today fails explicitly because its adapter is not installed. No audio endpoint is exposed.

### LOCAL_ONLY admission and transport rules

The backend validates policy and endpoint before calling the selected factory, then requires the factory's local-processing declaration. After creation it verifies local capabilities and the configured language. Each guarded transcription rechecks local capabilities before handing over audio; a remote or missing declaration produces a safe technical failure. No failure path selects another factory.

A configured endpoint must use `http` or `https` (scheme case-insensitive) and exactly the literal host `127.0.0.1` or `[::1]`, with an optional valid port and path; the `ProviderConfig` constructor enforces the same rule, so adapters can rely on it. Cloud/LAN addresses, wildcard addresses, DNS names (including `localhost`), abbreviated/encoded IP forms, credentials, query strings, fragments and malformed URIs fail closed. Validation performs no DNS lookup or network request and does not print rejected endpoint values. Use `FARO_SPEECH_ENDPOINT` or `--faro.speech.endpoint=http://127.0.0.1:8085` for a future installed service adapter; configuration alone cannot install one.

This is an admission gate for trusted, reviewed adapters, **not an OS egress firewall**. A provider declaration cannot prove the behavior of arbitrary Java code or a local proxy. Local adapters must use the validated endpoint, disable redirects/proxies and implicit model downloads, enforce transport deadlines and never forward audio or transcripts elsewhere, including on failure. Integration must test these transport obligations before adding a real adapter. The guarded selector cannot prevent a malicious adapter from lying; deployment network isolation remains a separate layer. Endpoint string representations are redacted; adapters must preserve the existing data-minimization rules.

`UNAVAILABLE`, `TIMEOUT`, incompatible requests, provider exceptions and null results remain technical failures with no cloud fallback. Assessment conversion remains future work; no academic judgment is inferred from an ASR failure.

FARO-010 provides a separately managed [local whisper.cpp baseline](../infra/whisper/README.md)
for synthetic Spanish WAV tests through the runtime's own `/inference` endpoint.
It does not register an application adapter, change the configuration above, or
replace the FARO-040 backend admission gate. Application audio normalization and integration remain FARO-012.

## Future integration configuration (mixed active and illustrative options)

```yaml
faro:
  privacy:
    policy: LOCAL_ONLY
  speech:
    provider: whisper-cpp # future: moonshine | vosk | fake
    language: es-CR
    timeout: 8s
    endpoint: http://127.0.0.1:8085 # active validation, no installed service adapter yet
    models: # future provider-specific options
      whisper-cpp:
        model: base-multilingual
  explanation:
    mode: RULES_ONLY # future: SHADOW_LLM
    provider: none
```

Provider-specific endpoints are Faro conventions, not vendor API guarantees. Switching from one *engine/runtime* to another still requires its installed adapter, model, configuration, license review, and passing contract tests; it must **not** require rewriting math, assessment, or UI.

## Verification and future integration tests

- Implemented: defensive audio copying, duration bounds, capability immutability, result provenance, redacted representations and safe failure codes.
- Implemented: deterministic fake results/failures, the shared guard (capabilities, language, timeout, failure normalization), configuration binding, required provider selection, test-only installed-whisper selection, unknown/unavailable provider and duplicate-registration failures without fallback.
- Future integration: real local adapter conformance, inference deadline enforcement and empty/unavailable results translated to retry/indeterminate feedback.
- Implemented by FARO-040: strict policy binding, pre-factory cloud/unknown-declaration veto, literal-loopback endpoint admission, local capability rechecks and no cloud factory creation or calls after local startup/runtime failures and timeout. See [verification](verification/local-only-policy.md). The existing API CI job runs these tests without models or network access.
- Future assessment/shadow work: six-versus-seven misrecognition never penalizes the child; malformed shadow LLM output cannot change child-visible feedback.
