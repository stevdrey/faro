# Local whisper.cpp baseline (FARO-010 / issue #11)

This standalone, adult-only technical baseline runs the official whisper.cpp CPU
image and the **base multilingual** model. A short synthetic Spanish WAV is sent
to `http://127.0.0.1:8085/inference`. It does not connect to Faro's Java API, change
its provider, capture a microphone, or enable a child pilot. FARO-012 owns
application integration; FARO-040 owns backend `LOCAL_ONLY` enforcement.

## Requirements and commands

- Linux **amd64**, Docker Engine, and Python **3.11+** (standard library only).
- A running **local** Docker daemon accessible to the current user. Do not use a
  remote Docker context or expose Docker's TCP API.
- eSpeak NG with the built-in `es-419` formant voice and FFmpeg with PCM WAV
  support, for `smoke-test` only. Verified with versions 1.52.0 and 8.1.3.
- Internet during explicit `setup`, disk space for the official image plus the
  147,951,465-byte model, and CPU/RAM for inference. No GPU or cloud credentials.

Run from the repository root:

```bash
python3 infra/whisper/baseline.py setup
python3 infra/whisper/baseline.py start
python3 infra/whisper/baseline.py smoke-test
python3 infra/whisper/baseline.py stop
```

`setup` stages the exact model revision in [artifacts.json](artifacts.json), checks
its byte length and SHA-256, and atomically installs it under ignored
`infra/whisper/models/`. It then pulls the official CPU image by immutable digest.
Repeated setup verifies and reuses existing weights. Corrupt existing weights
are rejected: remove that file explicitly and rerun setup. Interrupted downloads
are removed; no unverified model is installed.

`start` rechecks the entire model checksum, rejects model symlinks, and uses
`--pull=never`. It never downloads or switches models. It starts the fixed
`faro-whisper-baseline` container and waits up to approximately 120 seconds for
`/health`. Failed readiness or Ctrl+C removes its own container; exited containers
are automatically removed. Successful startup leaves the service running until
`stop`. Stop is idempotent and only targets the exact name and Faro ownership
label. Stop waits up to 30 seconds for Docker's automatic removal to release the
name before returning. Start does not replace an existing container; stop before restarting.

The container runs as UID/GID 65534, with capabilities dropped, no new privileges,
a read-only root filesystem, one read-only model mount and a 64 MiB `/tmp` tmpfs.
The model volume uses `:ro,Z` for private SELinux labeling of that public file.
Only `127.0.0.1:8085` is published. The server binds `0.0.0.0:8080` **inside the
container** for Docker port forwarding. Four CPU threads, language `es`, zero
prior-text context and the explicit multilingual model are selected. The image's
shell entrypoint is replaced with the direct executable. `--convert` is absent:
the upstream server reads the WAV in memory instead of writing conversion files.

`smoke-test` checks the managed baseline and `/health`, generates the manifest's
fixed Faro-authored phrase locally, and normalizes it to PCM16 little-endian mono
at 16 kHz. It validates the WAV format and positive duration of at most 30 seconds,
then posts multipart `file`, `language=es`, `translate=false`, `temperature=0.0`,
`response_format=json`. The socket timeout is 120 seconds and responses are
bounded to 1 MiB. Success requires a JSON object with nonblank string `text` and
no error. No expected-transcript prompt is sent; no exact-word accuracy is asserted.

Output contains only pass status, model, requested language, audio duration,
inference time and transcript character count. The transcript is not printed.
For optional manual review, submit only an **authorized adult-only synthetic WAV**:

```bash
curl --noproxy '*' --fail --silent --show-error --max-time 120 \
  -F 'file=@/absolute/path/to/synthetic.wav;type=audio/wav' \
  -F language=es -F translate=false -F temperature=0.0 -F response_format=json \
  http://127.0.0.1:8085/inference
```

This optional curl command displays the transcript. Do not save its response or
use real child audio. Tooling bypasses environment HTTP proxies, rejects local
HTTP redirects and has no configurable remote inference URL.

## Privacy and cleanup

Generated WAVs live in a private temporary directory (mode 0700); responses remain
in memory. Completion, tool/HTTP failures and Ctrl+C remove the directory. Docker's
`none` logging driver prevents persistence of server stdout/stderr. Errors expose
stable first-party codes only. Never commit recordings, transcripts, weights or
raw runtime diagnostics.

Setup requires downloads. Runtime has no downloader or cloud fallback in this
tool; Docker bridge networking is **not an outbound firewall** and does not
implement FARO-040. The unauthenticated upstream example is for local synthetic
testing, not LAN/Internet exposure or child data. Pilot consent requirements remain.

After stopping, public weights and the image stay cached. Remove only this
baseline's cached artifacts explicitly when no longer needed:

```bash
rm -- infra/whisper/models/ggml-base.bin
docker image rm ghcr.io/ggml-org/whisper.cpp@sha256:80eaf700df7e5c46ec7482f8123c88edaefefda74b36c1191a7c85c116e8e399
```

Do not prune unrelated resources. Abrupt host termination can bypass cleanup;
inspect your temporary directory and run `stop` if needed.

## Provenance and licenses

[artifacts.json](artifacts.json) records the image digest, source revision from
official BuildKit provenance, exact model revision/URL/size/SHA-256, and intended
use. Updates require review and another real smoke test. Execution does not resolve
mutable `main`/`latest` tags or unpinned model URLs.

| Artifact | Terms and provenance |
|---|---|
| Faro tooling | AGPL-3.0-or-later, like Faro-owned source |
| whisper.cpp runtime | [MIT at the pinned source revision](https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/LICENSE) |
| Multilingual `ggml-base.bin` | MIT metadata in the [exact converted-model revision](https://huggingface.co/ggerganov/whisper.cpp/blob/5359861c739e955e79d9a303bcbc70fb988958b1/README.md), derived from [OpenAI Whisper's MIT code and weights](https://github.com/openai/whisper#license). Checked separately from the runtime. |
| Official image | Ubuntu 22.04, libraries, curl, FFmpeg, SDL2, wget, CMake and Git have separate notices under `/usr/share/doc/*/copyright`. The [pinned Dockerfile](https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/.devops/main.Dockerfile) also copies unused `base.en` weights and source/build artifacts. Only the verified multilingual model is selected. The whole image is **not** universally MIT or a minimal Faro redistribution. |
| eSpeak NG | [GPL-3.0-or-later](https://github.com/espeak-ng/espeak-ng); built-in formant voice, no downloaded voice or human recording. Installed developer tool only. |
| FFmpeg | [LGPL/GPL depending on build](https://ffmpeg.org/legal.html); record the installed build for local verification. No FFmpeg binary is redistributed by Faro. |
| Synthetic sample | The manifest's Faro-authored phrase and generated sample are dedicated under CC0-1.0. No audio file is shipped. |

Digest pinning prevents silent image changes; it is not a security certification.
The general model inventory and redistribution review remain FARO-041's work.

## Tests and troubleshooting

```bash
python3 -m unittest discover -s infra/whisper/tests -v
```

The `whisper-baseline` CI job runs offline tests using runner-provided Python.
It never pulls the runtime, downloads weights or provisions voice tools. See
the [verification summary](../../docs/verification/local-whisper-baseline.md)
for actual acceptance results.

| Failure code | Action |
|---|---|
| `MODEL_MISSING_OR_SYMLINK` | Run setup; remove an unexpected model symlink explicitly. |
| `MODEL_SIZE_MISMATCH` / `MODEL_CHECKSUM_MISMATCH` | Remove the corrupt cached model and rerun setup. Never bypass verification. |
| `MODEL_DOWNLOAD_FAILED` / `IMAGE_PULL_FAILED` | Check Internet access and the local Docker daemon; retry setup. Partial model files are removed. |
| `DOCKER_UNAVAILABLE` / `DOCKER_START_FAILED` | Check daemon permissions/context, the cached image, existing container name and port 8085. No automatic sudo or alternate provider. |
| `CONTAINER_EXITED` / `SERVER_START_TIMEOUT` | Check amd64 support and available CPU/RAM. Arguments are tied to the pinned image. No raw logs are retained. |
| `BASELINE_NOT_RUNNING` / `SERVER_NOT_READY` | Start the managed baseline and wait for readiness. |
| `SYNTHESIS_FAILED` / `NORMALIZATION_FAILED` | Install eSpeak NG with `es-419` and FFmpeg. |
| `LOCAL_HTTP_*` / `INFERENCE_EMPTY_OR_INVALID_TEXT` | Check local health and retry the synthetic test. Failure bodies are redacted. |
| `START_FAILED_CLEANUP_FAILED` / `DOCKER_STOP_FAILED` / `DOCKER_REMOVE_TIMEOUT` | Restore daemon access and stop the managed baseline. |

API reference: [whisper-server at the pinned revision](https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/examples/server/README.md).
