// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechHypothesis;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.SpeechStatus;
import io.github.stevdrey.faro.speech.config.SpeechProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TranscriptionControllerTests {
    private final SpeechRecognizer recognizer = mock(SpeechRecognizer.class);
    private final TranscriptionController controller = new TranscriptionController(recognizer,
            new SpeechProperties("fake", "es-CR", Duration.ofSeconds(8), null));
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void forwardsBoundedPcmToThePortWithServerOwnedMetadataAndReturnsUnconfirmedHypothesis() throws Exception {
        when(recognizer.transcribe(any())).thenReturn(new SpeechHypothesis("seis", "es", "synthetic", "test", "1", 2, SpeechStatus.TRANSCRIBED));
        mvc.perform(post("/api/v1/speech/transcriptions").contentType("application/octet-stream").content(new byte[]{1, 2}))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.schemaVersion").value(1)).andExpect(jsonPath("$.transcript").value("seis"))
                .andExpect(jsonPath("$.languageTag").value("es")).andExpect(jsonPath("$.status").value("TRANSCRIBED"))
                .andExpect(jsonPath("$.modelVersion").value("1"));
        var request = ArgumentCaptor.forClass(io.github.stevdrey.faro.speech.SpeechRequest.class);
        verify(recognizer).transcribe(request.capture());
        assertThat(request.getValue().pcm16Mono()).isEqualTo(new byte[]{1, 2});
        assertThat(request.getValue().sampleRateHz()).isEqualTo(16_000);
        assertThat(request.getValue().languageTag()).isEqualTo("es-CR");
        assertThat(request.getValue().maxDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(request.getValue().correlationId()).matches("[a-f0-9-]{36}");
    }
    @Test
    void rejectsUnsupportedEmptyPartialAndOversizedAudioWithoutCallingProvider() throws Exception {
        mvc.perform(post("/api/v1/speech/transcriptions").contentType("audio/webm").content("private"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(content().json("{\"code\":\"UNSUPPORTED_FORMAT\"}"));
        for (var bytes : new byte[][]{new byte[0], new byte[1]}) {
            mvc.perform(post("/api/v1/speech/transcriptions").contentType("application/octet-stream").content(bytes))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/speech/transcriptions").contentType("application/octet-stream").content(new byte[960_002]))
                .andExpect(status().is(413));
        verifyNoInteractions(recognizer);
    }
    @Test
    void unknownLengthUploadsAreAlsoBounded() {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContentType("application/octet-stream");
        request.setContent(new byte[960_002]);
        assertThat(controller.transcribe(request).getStatusCode().value()).isEqualTo(413);
        verifyNoInteractions(recognizer);
    }
    @Test
    void failureCodesMapToSafeRetryResponses() throws Exception {
        for (var code : SpeechFailure.Code.values()) {
            doThrow(new SpeechFailure(code)).when(recognizer).transcribe(any());
            int expected = switch (code) {
                case TIMEOUT -> 504;
                case UNAVAILABLE -> 503;
                case PROVIDER_ERROR -> 502;
                case INCOMPATIBLE_REQUEST -> 400;
            };
            mvc.perform(post("/api/v1/speech/transcriptions").contentType("application/octet-stream").content(new byte[2]))
                    .andExpect(status().is(expected)).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.transcript").doesNotExist()).andExpect(jsonPath("$.trace").doesNotExist());
        }
    }
}
