# Browser audio → ASR verification (FARO-012 / issue #16)

## Scope and privacy boundary

This delivers the technical browser → PCM16 → `SpeechRecognizer` → whisper.cpp
path. **The preview stays off by default and is only for operator-declared
synthetic tests.** FARO-040 / #21 was merged in #73; its backend `LOCAL_ONLY`
admission now applies to the real whisper.cpp provider. The synthetic confirmation
cannot determine audio origin, establish guardian consent or authorize a pilot.
This preview does not provide protected adult review or enable child-audio use.

Ordinary startup, including `dev`, retains browser-only recording. Enabling only
the web preview cannot enable the API route; enabling only the API cannot cause
automatic uploads. The API requires loopback binding. The adapter requires an
explicit literal-loopback endpoint admitted by LOCAL_ONLY, with no proxy, redirects, runtime downloads or cloud
fallback. Use the managed baseline with its reviewed manifest, verified model
and disabled Docker logging. No dependency, weights or audio fixture is added.

## Automated checks

```bash
gradle -p services/api test
npm --prefix apps/web run test:unit
npm --prefix apps/web run build
npm --prefix apps/web run test:e2e
node .agents/scripts/pr-size-preflight.mjs
```

- Java: preview-off configuration, explicit adapter selection/provenance,
  loopback binding, PCM limits including unknown-length requests, safe error
  responses, WAV fields/sample bytes, Spanish options, blank/malformed/oversized
  results (oversized → `PROVIDER_ERROR`), overlap rejected until an abandoned
  exchange's runtime replies, drain limit for stalled response bodies,
  redirects and downtime. The guard's own tests cover the inference deadline.
  Shared limits derive from one sample-rate/duration pair.
- Web units: PCM endianness, clipping, silence, stereo/multi-channel averaging,
  trimming the 30 s auto-stop overrun, duration/size limits, invalid
  samples/channels, result validation, duplicate activation,
  cancellation during decoding/upload, deadlines and rejection of late results
  without exposing vendor exception text.
- Chromium: real MediaRecorder synthetic input, native decoding, normalized PCM
  transport, explicit activation, unsupported decoding without upload, neutral
  unavailable/empty/malformed results and retry, unchanged completed attempts,
  empty storage and lifecycle/context cleanup. Playwright starts two web servers:
  the ordinary suite runs on 5173 with the preview disabled (and asserts that
  the default build offers no transcription), while `transcription.spec.ts`
  uses an owned server on 5174 with `VITE_FARO_SYNTHETIC_ASR=true`. Preview
  tests intercept transport and need no API/model. Browser
  traces/screenshots/videos stay disabled. Existing CI API/web jobs run these.
  Keep port 5174 free; outside CI, an existing default server on 5173 is reused.
  Lifecycle cases use a generated Web Audio stream with real MediaRecorder;
  the transport case and optional smoke use Chromium's fake microphone. Native
  resampling is checked for exact sample count and a small filter-gain tolerance;
  PCM encoding itself has exact unit assertions.

## Optional real end-to-end smoke

Requires the existing local baseline setup; no test downloads models or starts
Docker implicitly. Start the baseline and API in separate terminals:

```bash
python3 infra/whisper/baseline.py start
gradle -p services/api bootRun --args='--faro.speech.provider=whisper-cpp --faro.speech.synthetic-test.enabled=true --faro.speech.endpoint=http://127.0.0.1:8085/inference'
```

With port 5174 free:

```bash
FARO_REAL_ASR_SMOKE=true npm --prefix apps/web run test:e2e -- transcription.spec.ts --grep 'optional real browser'
```

The test generates the manifest's Faro-authored Spanish phrase with existing
eSpeak NG/FFmpeg tools in a private temporary directory. Chromium receives this
synthetic 48 kHz stereo WAV through its fake microphone, records five seconds
with **real MediaRecorder**, and uses application controls to normalize/send it
through the real API and runtime. Success requires nonblank recognized text,
expected model/revision and effective `es` language, without asserting word
accuracy. Only status, engine, language, latency and character count are printed;
no transcript/audio is retained as verification evidence. Cleanup closes the
browser and removes generated WAVs on success/errors. This smoke is explicitly
skipped during ordinary CI.

Stop the API with Ctrl+C, then `python3 infra/whisper/baseline.py stop`.
For manual synthetic operation, start the web server with
`VITE_FARO_SYNTHETIC_ASR=true npm --prefix apps/web run dev`, complete an exercise,
record **synthetic playback only**, confirm synthetic origin and choose
**Transcribir prueba**. Optional details label the transcript unconfirmed.
Delete/re-record/restart/hide clears it. Do not use a child's voice.
Firefox/Safari codec compatibility and physical-device usability remain manual;
unsupported formats must leave the activity usable.

## Verification results

Memory cleanup discards references; browser/JVM
garbage collectors control reclamation. HTTP cancellation invalidates results
without promising immediate interruption of already accepted inference.
Web Audio decoding is not abortable; its late completion is ignored and cannot
upload a cancelled clip.

Original FARO-012 verification on **2026-10-23 (America/Costa_Rica)**:

- Java: **493 tests passed**, zero failures/errors/skips; the adapter tests also
  passed **3/3** repeated runs.
- Web units: **105 passed**. Type checks and production build passed with the
  preview disabled by default (the bundle contains no preview controls).
- Browser: the full run passed **138 tests** (125 default-build tests and 13
  preview tests); the optional real smoke is skipped there.
- Real browser → API → pinned whisper.cpp smoke: **passed** before the review
  fixes (bounded overrun trimming, permit release and the two-server Playwright
  setup); it was not re-run afterwards. Five seconds of
  browser-recorded synthetic input yielded `TRANSCRIBED`, effective language
  `es`, expected model/revision, **1,090 ms** adapter latency and **73 transcript
  characters**. No exact-word accuracy claim is made and no transcript is
  retained in this summary. Temporary WAVs, test browsers, API and baseline
  started for verification were cleaned up.
- PR size preflight: **25/40 files**, within the ordinary 0–30 range;
  `git diff --check` passed. No dependency manifest/lockfile changed.

Environment: Node **24.21.0**, npm CLI **12.2.0**, JDK **27**, Chromium provided by
the locked Playwright installation. Local Gradle is **9.8.2**, while repository CI
pins **9.8.1**; that exact CI Gradle version was not exercised locally. No remote
CI or protected branch setting is claimed by these original results. FARO-040
was subsequently merged in #73 and integrated below. The preview remains
synthetic-only and disabled by default.

### Integration with merged FARO-040 (#21 / PR #73)

The branch incorporates `main` commit `98618f9`. `WhisperCppProvider` explicitly
declares local processing and requires `faro.speech.endpoint` with its full
`/inference` path. The recognizer uses that admitted URI instead of an implicit
fixed address. Missing endpoints and unsupported paths fail startup; remote
endpoints and non-LOCAL_ONLY policies are vetoed by the shared backend gate.
The web/API preview flags remain off by default and the feature stays synthetic-only.

Local integration verification on **2026-10-23 (America/Costa_Rica)**:

- Full Java suite: **546 passed**, zero failures/errors/skips. Added real-provider
  configuration coverage for missing/incorrect endpoints, cloud/policy veto and
  successful transcription through the guard to a configured ephemeral local
  HTTP server, demonstrating that the configured address is actually used.
- Web units: **105 passed**; type checks and default production build passed.
- Targeted Chromium preview: **13 passed**, with the optional real smoke skipped
  in the ordinary run. The prior full-browser regression remains recorded above.
- Real browser → LOCAL_ONLY API → pinned whisper.cpp smoke: **passed again**,
  now after the review fixes and policy integration. Generated Spanish speech
  recorded by the browser yielded `TRANSCRIBED`, effective language `es`, expected
  model/revision, **2,885 ms** adapter latency and **81 transcript characters**.
  These are technical success metrics, not accuracy claims; no transcript/audio
  is retained in the report. The test browser, temporary audio, API and baseline
  started for this check were cleaned up.
- Resulting PR scope: **25 files** relative to the updated `main`, within the
  ordinary 0–30 range. `git diff --check` passed; no dependencies changed.

Local environment remains Node 24.21.0, npm CLI 12.2.0, JDK 27 and Gradle 9.8.2
(CI pins Gradle 9.8.1). The dependency on #21 is resolved; pilot consent,
protected adult review and child-audio enablement remain outside this preview.

References: [Web Audio complete-file decoding and resampling](https://developer.mozilla.org/en-US/docs/Web/API/BaseAudioContext/decodeAudioData)
and the [pinned whisper.cpp server protocol](https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/examples/server/server.cpp).
