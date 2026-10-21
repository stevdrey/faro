// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.speech.AsrCapabilities;
import io.github.stevdrey.faro.speech.ProviderConfig;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechHypothesis;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.SpeechRequest;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enforces the contract once for every adapter: capabilities, configured language, inference deadline and
 * normalized failures. Vendor exceptions are dropped, never attached.
 */
final class GuardedSpeechRecognizer implements SpeechRecognizer {
    private final SpeechRecognizer delegate;
    private final String language;
    private final Duration timeout;

    GuardedSpeechRecognizer(SpeechRecognizer delegate, ProviderConfig config) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.language = config.language();
        this.timeout = config.timeout();
    }

    @Override
    public SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure {
        Objects.requireNonNull(request, "request");
        if (!request.languageTag().equals(language) || !delegate.capabilities().supports(request)) {
            throw new SpeechFailure(SpeechFailure.Code.INCOMPATIBLE_REQUEST);
        }
        var task = new FutureTask<>(() -> delegate.transcribe(request));
        Thread.ofVirtual().name("faro-asr").start(task);
        try {
            var result = task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (result == null) {
                throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
            }
            return result;
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new SpeechFailure(SpeechFailure.Code.TIMEOUT);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SpeechFailure failure) {
                throw failure;
            }
            throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        }
    }

    @Override
    public AsrCapabilities capabilities() {
        return delegate.capabilities();
    }
}
