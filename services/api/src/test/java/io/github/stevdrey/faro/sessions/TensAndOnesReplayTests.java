// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TensAndOnesReplayTests {
    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void removesTwentyThenEightWithAnExplicitExchange() {
        var events = new ArrayList<TraceEvent>();
        events.add(new ActionEvent.RemoveBlock(10, 1, AT));
        events.add(new ActionEvent.RemoveBlock(10, 2, AT));
        assertThat(replay(53, events)).isEqualTo(new TensAndOnesReplay.Result(3, 3, 2, 0, false));
        events.add(new ExchangeEvent.DecomposeTen(3, AT.minusSeconds(1)));
        assertThat(replay(53, events)).isEqualTo(new TensAndOnesReplay.Result(2, 13, 2, 0, false));
        for (int i = 0; i < 8; i++) events.add(new ActionEvent.RemoveBlock(1, events.size() + 1, AT));
        events.add(new ActionEvent.FinishAttempt(events.size() + 1, AT));
        var result = replay(53, events);
        assertThat(result).isEqualTo(new TensAndOnesReplay.Result(2, 5, 2, 8, true));
        assertThat(result.remainingValue()).isEqualTo(25);
        assertThat(result.removedValue()).isEqualTo(28);
        assertThat(replay(53, events)).isEqualTo(result);
    }

    @Test
    void supportsReverseExchangesAndCorrectionsWithoutMutatingTheTrace() {
        var events = List.<TraceEvent>of(
                new ExchangeEvent.DecomposeTen(1, AT), new ActionEvent.RemoveBlock(1, 2, AT),
                new ActionEvent.PlaceBlock(1, 3, AT), new ExchangeEvent.ComposeTen(4, AT),
                new ActionEvent.RemoveBlock(10, 5, AT), new ActionEvent.PlaceBlock(10, 6, AT));
        var trace = new ExtendedActionTrace(events);
        assertThat(TensAndOnesReplay.replay(53, trace)).isEqualTo(new TensAndOnesReplay.Result(5, 3, 0, 0, false));
        assertThat(trace.events()).containsExactlyElementsOf(events);
    }

    @Test
    void canRemoveEverythingAndReturnAllPieces() {
        var events = new ArrayList<TraceEvent>();
        for (int i = 0; i < 5; i++) events.add(new ActionEvent.RemoveBlock(10, events.size() + 1, AT));
        for (int i = 0; i < 3; i++) events.add(new ActionEvent.RemoveBlock(1, events.size() + 1, AT));
        assertThat(replay(53, events)).isEqualTo(new TensAndOnesReplay.Result(0, 0, 5, 3, false));
        for (int i = 0; i < 5; i++) events.add(new ActionEvent.PlaceBlock(10, events.size() + 1, AT));
        for (int i = 0; i < 3; i++) events.add(new ActionEvent.PlaceBlock(1, events.size() + 1, AT));
        assertThat(replay(53, events)).isEqualTo(new TensAndOnesReplay.Result(5, 3, 0, 0, false));
    }

    @Test
    void rejectsImpossibleMovesAndDoesNotBorrowOrCombineAcrossTrays() {
        for (TraceEvent event : List.<TraceEvent>of(
                new ActionEvent.PlaceBlock(1, 1, AT), new ActionEvent.PlaceBlock(10, 1, AT),
                new ActionEvent.RemoveBlock(8, 1, AT), new ActionEvent.RemoveBlock(20, 1, AT),
                new ActionEvent.JumpLeft(1, 1, AT), new ActionEvent.JumpRight(1, 1, AT),
                new ExchangeEvent.ComposeTen(1, AT))) {
            assertThatThrownBy(() -> replay(53, List.of(event))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> replay(0, List.of(new ExchangeEvent.DecomposeTen(1, AT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> replay(10, List.of(new ActionEvent.RemoveBlock(1, 1, AT))))
                .isInstanceOf(IllegalArgumentException.class);
        var events = new ArrayList<TraceEvent>();
        events.add(new ExchangeEvent.DecomposeTen(1, AT));
        for (int i = 0; i < 4; i++) events.add(new ActionEvent.RemoveBlock(1, events.size() + 1, AT));
        events.add(new ExchangeEvent.ComposeTen(events.size() + 1, AT));
        assertThatThrownBy(() -> replay(53, events)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 53, Integer.MAX_VALUE})
    void emptyAndImmediateCompletionAreValid(int initial) {
        var result = replay(initial, List.of());
        assertThat(result.remainingValue()).isEqualTo(initial);
        assertThat(result.removedValue()).isZero();
        assertThat(result.completed()).isFalse();
        assertThat(replay(initial, List.of(new ActionEvent.FinishAttempt(1, AT))).completed()).isTrue();
    }

    @Test
    void maximumQuantityCanBeDecomposedWithoutOverflow() {
        var result = replay(Integer.MAX_VALUE, List.of(new ExchangeEvent.DecomposeTen(1, AT)));
        assertThat(result.remainingValue()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.ones()).isEqualTo(17);
        assertThatThrownBy(() -> replay(-1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TensAndOnesReplay.replay(53, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exchangeMetadataAndTraceFinalizationRemainStrict() {
        assertThatThrownBy(() -> new ExchangeEvent.DecomposeTen(0, AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExchangeEvent.ComposeTen(-1, AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExchangeEvent.DecomposeTen(1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExchangeEvent.ComposeTen(1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExtendedActionTrace(List.of(new ExchangeEvent.DecomposeTen(2, AT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtendedActionTrace(List.of(new ActionEvent.FinishAttempt(1, AT), new ExchangeEvent.ComposeTen(2, AT))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TensAndOnesReplay.Result replay(int initial, List<TraceEvent> events) {
        return TensAndOnesReplay.replay(initial, new ExtendedActionTrace(events));
    }
}
