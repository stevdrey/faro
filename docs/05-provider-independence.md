# Vendor-independent speech recognition and future interpretation

**Status:** ASR contracts, fake adapter and selection implemented by FARO-031. FARO-012 adds an opt-in **synthetic-only** browser/whisper.cpp preview; FARO-040 privacy enforcement remains pending and gates child audio. **MVP 0.1 target:** `RULES_ONLY`, on-device or local-machine ASR, `LOCAL_ONLY` network/privacy policy; no remote audio fallback.

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

- Requests carry transient signed PCM16 **little-endian mono** samples. Empty audio is valid. Sample rate and maximum audio duration must be positive; incomplete samples and audio exceeding the declared duration are rejected. The byte array is copied on construction and access, and requests have value equality over the audio content. `maxDuration` is the caller's bound; `audioDuration()` is the actual sample length. `ProviderConfig.timeout` is the separate inference deadline.
- Capabilities contain immutable, nonempty sets of positive rates and language tags, maximum audio duration and a local-processing declaration. All language tags (requests, results, capabilities, configuration) must be well-formed BCP 47 and are normalized to canonical case, so `es-cr` equals `es-CR`; ill-formed tags are rejected, and aliases or fallback are not inferred. `supports(request)` requires a supported rate/language and actual audio length within the adapter's maximum, regardless of the caller's declared bound. The declaration alone does **not** enforce `LOCAL_ONLY`.
- Results preserve transcript text exactly, effective language, engine, model, revision and nonnegative latency. `TRANSCRIBED` requires nonblank text; `EMPTY` and `UNAVAILABLE` require blank text (whitespace-only engine output for silence is preserved, not rejected). `UNRELIABLE` can retain an uncertain transcript or have none. There are no segments or confidence fields yet.
- Adapter operational failures throw `SpeechFailure` with `UNAVAILABLE`, `TIMEOUT`, `INCOMPATIBLE_REQUEST` or `PROVIDER_ERROR`. `UNAVAILABLE` in `SpeechStatus` is reserved for a future application-level conversion of failures; adapters use the exception channel. No assessment conversion is implemented here.
- Request/result string representations redact sensitive content and metadata. Failure messages contain only the stable code; vendor exception causes and suppressed exceptions cannot be attached. These contracts provide no storage or logging. Future adapters must not log or persist audio/transcripts, and must normalize vendor errors without copying raw messages.

### Fake adapter and registration

The production fake is deliberately non-recognizing: compatible empty or nonempty audio returns an `EMPTY` hypothesis with `fake` / `synthetic` / `1` provenance and zero latency. It advertises 16 kHz, `es-CR`, at most 30 seconds, and local processing. Tests can construct it with a synthetic `TRANSCRIBED`/`EMPTY`/`UNRELIABLE` hypothesis or a failure code, without clocks, network, processes or model weights. It never retains input requests.

Installed adapters register an `AsrProvider` Spring bean with a unique `id()` and `create(ProviderConfig)` factory. `SpeechConfiguration` validates all registration IDs, invokes **only** the explicitly selected factory, verifies the configured language is supported and exposes one guarded `SpeechRecognizer` bean. The guard enforces, once for every adapter, the adapter's capabilities, the configured language and the inference timeout (`TIMEOUT`, with the adapter call interrupted); unchecked adapter exceptions and null results become `PROVIDER_ERROR` without attaching the vendor exception. Unknown or absent providers, duplicate IDs, unsupported language and invalid configuration fail startup with messages naming the provider id or language (configuration values, never child data). A selected factory's `SpeechFailure` is propagated and unchecked factory exceptions are replaced by `PROVIDER_ERROR` without their message; no alternate factory is invoked. The pure port and data objects do not import Spring or vendor SDKs.

FARO-031's original selection tests still use a test-only installed recognizer.
FARO-012 separately registers the real `WhisperCppProvider` only when
`faro.speech.synthetic-test.enabled=true`. It is not available in ordinary startup.

## Implementation policy

- `SpeechRecognizer` adapters: fake for deterministic tests, then local whisper.cpp reference. Moonshine Spanish Tiny Streaming and Vosk small-es are alternative **adapters**, not implicit fallbacks.
- `ExplanationInterpreter`: rules/number parser first. Optional Qwen/Ollama, Claude, Gemini, OpenAI, Grok, Kimi, or other adapters only after separate review; LLM **shadow mode cannot affect child-facing decisions**.
- Business domain and assessment cannot import vendor SDKs or depend on a specific model result schema. Adapters normalize only transport/format, not meaning.
- Model selection is explicit in backend configuration. An unavailable local provider raises a technical failure; future consumers should translate it to unavailable/non-evaluable feedback, **never** automatically call a cloud service.
- Preserve engine, model, revision, effective language, latency, and technical status without logging child audio/transcript.
- License review covers runtime, exact weights, tokenizers, voices, and redistribution rights **individually**.

## Active configuration

`faro.speech.provider` has **no default**: startup fails until it is set, so a deployment can never silently run the non-recognizing fake. The fake is selected only by the `dev` profile (`application-dev.yaml`) and by the test-only `config/application.yaml` overlay. `language` (`es-CR`) and `timeout` (`8s`) keep binding defaults. Properties can be overridden through normal Spring Boot configuration, for example `FARO_SPEECH_PROVIDER` or `--faro.speech.timeout=3s`. Provider/language must be nonblank and timeout must be positive. Run locally with `--spring.profiles.active=dev`. The fake currently supports only `es-CR`.

```yaml
# application.yaml (provider intentionally absent)
faro:
  speech:
    language: es-CR
    timeout: 8s
---
# application-dev.yaml
faro:
  speech:
    provider: fake
```

With the preview disabled, choosing `whisper-cpp` still fails because its adapter
is not installed, and no audio endpoint is exposed. This selection mechanism is
**not** the FARO-040 backend network safety gate.

FARO-010 provides a separately managed [local whisper.cpp baseline](../infra/whisper/README.md)
for synthetic Spanish WAV tests through the runtime's own `/inference` endpoint.
The baseline itself does not register an application adapter, change the
configuration above, or implement FARO-040.

## FARO-012 synthetic integration

Two explicit opt-ins are required: `VITE_FARO_SYNTHETIC_ASR=true` for the web
server/build and `faro.speech.synthetic-test.enabled=true` for the API. Neither
is enabled by `dev`. The preview requires `server.address=127.0.0.1` and remains
synthetic-only until #21 is delivered. Synthetic confirmation is an operator
declaration, not verification of audio origin, consent or authentication.

`POST /api/v1/speech/transcriptions` accepts **application/octet-stream** containing
raw signed PCM16 LE mono samples at 16 kHz. No filename, exercise, identity,
provider, URL, language or client duration is accepted. The API sets configured
language, generates a transient correlation UUID and bounds actual audio by
sample count (960,000 bytes / 30 seconds). Empty/odd-length input is rejected.
Declared-length and chunked bodies are bounded in memory; multipart parsing is
disabled so unsupported uploads cannot spool audio to disk.

Success JSON contains `schemaVersion: 1` and the existing hypothesis fields:
`transcript`, `languageTag`, `engineId`, `modelId`, `modelVersion`, `latencyMs`,
`status`. Responses use `Cache-Control: no-store`. Errors return only `{code}`:
400 `INVALID_AUDIO`, 413 `AUDIO_LIMIT`, 415 `UNSUPPORTED_FORMAT`, 502
`PROVIDER_ERROR`, 503 `UNAVAILABLE` or 504 `TIMEOUT`. The browser offers technical
retry states and never grades the attempt.

Web Audio decodes the complete recording and resamples to 16 kHz; one or two
channels are averaged and saturated into PCM16. Encoded input is limited to
8 MiB and decoded audio to 30 seconds. Codec support depends on the browser;
there is no codec download or conversion fallback. The browser applies a
15-second decode/upload deadline; backend inference defaults to 8 seconds.

The adapter wraps PCM in an in-memory WAV and posts `file`, `language=es`,
`response_format=json`, `temperature=0.0`, `translate=false` to the fixed literal
`http://127.0.0.1:8085/inference`. It bypasses proxies, rejects redirects and
bounds response bytes (64 KiB), text (16,000 characters), the whole inference
exchange and concurrent inference (one, with overlap unavailable).
Empty PCM avoids the runtime; blank text becomes `EMPTY`; malformed JSON and
vendor failures become safe `SpeechFailure` codes. No alternate provider is tried.
Effective language is **`es`**: the runtime does not distinguish Costa Rican
Spanish. Model id/revision come from the reviewed baseline manifest packaged by
Gradle. They describe the expected deployment; use the checksum-verified baseline.
The transport cannot attest an arbitrary service running on that local port.

Completed snapshots and action contracts remain unchanged. The hypothesis lives
only with the transient clip; context changes/cancellation invalidate late
results. No private data is stored or logged. Browser abort does not promise
that inference already accepted stops immediately; backend deadlines still apply.
See [verification](verification/browser-asr-integration.md). The general provider
veto, network policy and authorization for child audio remain FARO-040.

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
- Implemented: deterministic fake results/failures, the shared guard (capabilities, language, timeout, failure normalization), configuration binding, required provider selection, test-only installed-whisper selection, unknown/unavailable provider and duplicate-registration failures without fallback.
- Implemented for synthetic preview: local adapter conformance, bounded WAV transport, inference deadlines and empty/unavailable results translated to neutral retry states.
- FARO-040: `LOCAL_ONLY` blocks remote execution even if a remote endpoint is configured.
- Future assessment/shadow work: six-versus-seven misrecognition never penalizes the child; malformed shadow LLM output cannot change child-visible feedback.
