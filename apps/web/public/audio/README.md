# Prerecorded UI guidance

These seven Ogg Opus files (mono, 24 kbps, about 20–37 KB each) are **synthetic UI
instructions**, generated offline with eSpeak NG 1.52.0's built-in `es-419` formant
voice, 155 words/minute, pitch 50, then encoded with FFmpeg 8.1.3's `libopus`.
They contain no human recordings, child data, model weights, or sampled voices.
The wording uses Costa Rican Spanish; the synthesized accent is generic Latin
American Spanish and still needs a supervised listening/usability review.

The Faro-authored prompt text and generated UI audio in this directory are
dedicated under **CC0-1.0**. Generation code remains AGPL-3.0-or-later.
eSpeak NG (GPL-3.0-or-later) and FFmpeg/libopus are separate development tools, not
bundled or used at runtime. See the [eSpeak NG](https://github.com/espeak-ng/espeak-ng)
and [FFmpeg](https://ffmpeg.org/legal.html) source and licenses.

`manifest.json` records the exact text, generator settings and SHA-256 of each
committed clip. Source text lives in `src/features/guidance/clips.ts`; the browser
only plays same-origin files, with no runtime synthesis or remote fallback.
Never place private voice recordings in this public directory.

To regenerate deliberately, with Node 24, eSpeak NG 1.52.0 and FFmpeg 8.1.3 (with
`libopus`) already installed:

```bash
node apps/web/scripts/generate-guidance.ts
```

The script builds every clip in a temporary directory and replaces the committed
clips and manifest only after all succeed. Bit-exact encoding makes reruns produce
identical checksums. No install/build hook runs this script or downloads voices. To replace clips
with reviewed adult recordings later, retain their IDs, update transcripts and
checksums, and document the new speaker's distribution permission and license.

`recordingStart` and `recordingStop` are ready for FARO-011's future push-to-talk
controls. They are not exposed in today's demos: microphone capture, recording
permissions, cancellation and consent are not implemented by FARO-009.
