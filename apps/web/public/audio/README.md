# Prerecorded UI guidance

These seven WAV files are **synthetic UI instructions**, generated offline with
eSpeak NG 1.52.0's built-in `es-419` formant voice, 155 words/minute, pitch 50.
They contain no human recordings, child data, model weights, or sampled voices.
The wording uses Costa Rican Spanish; the synthesized accent is generic Latin
American Spanish and still needs a supervised listening/usability review.

The Faro-authored prompt text and generated UI audio in this directory are
dedicated under **CC0-1.0**. Generation code remains AGPL-3.0-or-later.
eSpeak NG is a separate GPL-3.0-or-later development tool, not bundled or used at
runtime. See its [source and license](https://github.com/espeak-ng/espeak-ng).

`manifest.json` records the exact text, generator settings and SHA-256 of each
committed clip. Source text lives in `src/features/guidance/clips.ts`; the browser
only plays same-origin files, with no runtime synthesis or remote fallback.
Never place private voice recordings in this public directory.

To regenerate deliberately, with Node 24 and eSpeak NG 1.52.0 already installed:

```bash
node apps/web/scripts/generate-guidance.ts
```

No install/build hook runs this script or downloads voices. To replace clips
with reviewed adult recordings later, retain their IDs, update transcripts and
checksums, and document the new speaker's distribution permission and license.

`recordingStart` and `recordingStop` are ready for FARO-011's future push-to-talk
controls. They are not exposed in today's demos: microphone capture, recording
permissions, cancellation and consent are not implemented by FARO-009.
