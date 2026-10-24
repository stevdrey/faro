// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.adapters;

import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.ProviderConfig;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import tools.jackson.databind.json.JsonMapper;

/** Only registered by the opt-in synthetic preview. Uses the independently managed, pinned baseline. */
public final class WhisperCppProvider implements AsrProvider {
    @Override
    public String id() { return "whisper-cpp"; }

    @Override
    public SpeechRecognizer create(ProviderConfig config) throws SpeechFailure {
        try (var input = getClass().getResourceAsStream("/speech/whisper-artifacts.json")) {
            if (input == null) throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
            var manifest = JsonMapper.builder().build().readTree(input);
            return new WhisperCppRecognizer(config.timeout(), manifest.get("model").get("id").asString(),
                    manifest.get("model").get("revision").asString());
        } catch (SpeechFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        }
    }
}
