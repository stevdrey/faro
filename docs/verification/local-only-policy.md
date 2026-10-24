# FARO-040 — LOCAL_ONLY backend verification

Issue: [#21](https://github.com/stevdrey/faro/issues/21). Implementation builds on
FARO-031's provider-independent ASR port. No new dependencies, model weights,
audio endpoint, browser changes or assessment rules are introduced.

## Behavior

- `faro.privacy.policy` defaults to `LOCAL_ONLY`. Blank, unknown or cloud-enabling
  values fail startup; the policy cannot be disabled through configuration.
- Provider selection remains explicit. Missing/unknown providers fail startup;
  `fake` is still selected only by the dev profile or test configuration.
- Installed factories must explicitly declare local processing. The default is
  denial, checked before `create`, even for an adapter named `whisper-cpp`.
  Unselected factories are never invoked.
- Optional `faro.speech.endpoint` is validated before factory invocation and
  passed through `ProviderConfig`. Only HTTP(S) (scheme case-insensitive) on
  literal `127.0.0.1` or `[::1]` with an optional valid port/path is admitted.
  DNS names (including `localhost`), cloud/LAN/wildcard addresses, alternate IP
  forms, other schemes, credentials, query strings, fragments and malformed
  values are rejected without echoing the endpoint. Validation performs no DNS
  resolution or network I/O. The `ProviderConfig` constructor enforces the same
  rule, so no instance can carry a non-loopback endpoint.
- Undeclared, remote and failing local-processing declarations are vetoed with
  a `SpeechFailure` cause (`UNAVAILABLE`, or `PROVIDER_ERROR` when the
  declaration throws); vendor messages are dropped.
- Created recognizers must advertise local capabilities. Each transcription
  rechecks them before delivering audio; remote or missing capabilities fail
  with `PROVIDER_ERROR`. Vendor capability exceptions are normalized too: at
  startup they are reported as a provider fault, not as a LOCAL_ONLY rejection.
- Local startup failure, all `SpeechFailure` codes, unchecked exceptions, null
  results and inference timeout never select another factory. Timeout interrupts
  the local call; no cloud recognizer is created or called.
- Endpoint string representations are redacted. Audio and transcript contracts
  retain their existing copying, redaction and safe-error behavior.

Example for the existing fake (no service endpoint needed):

```bash
gradle -p services/api bootRun --args='--spring.profiles.active=dev'
```

`--faro.privacy.policy=CLOUD_ALLOWED` or
`--faro.speech.endpoint=https://cloud.invalid/inference` must fail startup.
`whisper-cpp` without an installed factory also fails explicitly. A valid
loopback endpoint alone does not install an adapter.

## Automated coverage and results

```bash
gradle -p services/api --no-daemon test --tests '*speech*'
gradle -p services/api --no-daemon test
node .agents/scripts/pr-size-preflight.mjs
git diff --check
```

`LocalOnlyConfigurationTests` contains 50 synthetic test cases for default and
explicit policy, invalid policy before creation, remote/undeclared/failing
declaration veto, endpoint rejection (at binding and in `ProviderConfig`)
and redaction, and validated endpoint delivery to a test-only installed whisper
factory. Startup/runtime failures, null/remote/failing capabilities,
null results and timeout are covered with counters proving zero cloud factory
creations and zero cloud transcription calls. Existing `SpeechConfigurationTests`
cover fake/whisper selection, unknown providers, duplicate registration and
factory errors. `FaroApplicationTests` verifies the effective privacy policy in
the full Spring context.

Local verification on 2026-10-23: the speech subset and full API suite passed;
the full suite ran **520 tests, zero failures/errors/skips**, using Java 27 and
installed Gradle 9.8.2. The repository/CI pins Gradle 9.8.1, which was not installed
locally. No lockfiles or verification metadata were changed. The existing API CI
job runs the complete suite, including these regressions, with no real ASR runtime
or model downloads; remote CI results are recorded on the pull request.

## Integration boundary

This is a backend admission gate for trusted adapters, not an OS outbound
firewall. Provider declarations cannot prove arbitrary Java code or a local
proxy's behavior. Adapter constructors/registration must perform no I/O; reviewed
local transports must use the validated endpoint, disable redirects/proxies and
implicit downloads, enforce deadlines, and never forward audio/transcripts on
failure. A real whisper.cpp adapter and tests of its actual HTTP transport remain
FARO-012 work. The standalone FARO-010 Docker baseline is independent of this
selector. Assessment conversion of technical ASR failures also remains separate.
