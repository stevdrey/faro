# Open-source licensing and possible commercial path

**Status: proposal, not legal advice or an adopted license.** The source recommends `AGPL-3.0-or-later` for Faro-owned code; a root `LICENSE` file is intentionally deferred until explicit selection.

## Proposed outcome

- Allow families, schools, and developers to use, study, modify, and self-host the application.
- Fund optional inexpensive hosted operation and support if the pilot proves viable.
- Under AGPL, an organization operating a modified network-interactive copy must comply with source-offer obligations for that AGPL-covered modified program; AGPL does not prohibit hosting competitors or require revealing customers' data and unrelated programs.

## Separate rights by artifact

Faro-owned source, whisper.cpp runtime, each downloaded Whisper/Moonshine/Vosk set of model weights, future Qwen-family weights, UI assets, pre-recorded voice, and other third-party libraries have **independent rights**. A permissively licensed runtime does not imply that every model checkpoint has the same license.

The exact **Moonshine Spanish Tiny/Small Streaming** artifacts are listed as MIT in the source; an older **Spanish Base** line may have noncommercial restrictions. Maintain a machine-readable model manifest with model ID, runtime, revision, download provenance, checksum, license and intended use. FARO-041 owns this inventory.

Dual licensing can be considered later only after checking copyright ownership and contribution policy. Brand and logos may be separate from source-code licensing. Before inviting other families, arrange a professional review of child data, applicable Costa Rican and destination law, terms, consent, providers and accessibility.

References: [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.html), [GNU FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#UnreleasedModsAGPL), [Vosk licenses](https://alphacephei.com/vosk/models), [Moonshine licenses](https://moonshine-voice.readthedocs.io/en/stable/license/).
