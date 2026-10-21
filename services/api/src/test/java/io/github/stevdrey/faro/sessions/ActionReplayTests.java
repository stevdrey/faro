// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActionReplayTests {
    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");

    @FunctionalInterface
    interface MovementFactory {
        ActionEvent create(int value, int step, Instant occurredAt);
    }

    static Stream<Arguments> movements() {
        return Stream.of(
                Arguments.of("placeBlock", (MovementFactory) ActionEvent.PlaceBlock::new),
                Arguments.of("removeBlock", (MovementFactory) ActionEvent.RemoveBlock::new),
                Arguments.of("jumpLeft", (MovementFactory) ActionEvent.JumpLeft::new),
                Arguments.of("jumpRight", (MovementFactory) ActionEvent.JumpRight::new));
    }

    @ParameterizedTest(name = "{0}: valid metadata and positive magnitudes")
    @MethodSource("movements")
    void validatesEveryMovement(String name, MovementFactory factory) {
        assertThat(factory.create(1, 1, AT).step()).isEqualTo(1);
        assertThat(factory.create(Integer.MAX_VALUE, 1, AT).occurredAt()).isEqualTo(AT);
        for (int invalid : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThatThrownBy(() -> factory.create(invalid, 1, AT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> factory.create(1, invalid, AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> factory.create(1, 1, null))
                .isInstanceOf(NullPointerException.class).hasMessage("occurredAt");
    }

    @Test
    void validatesFinishMetadata() {
        assertThat(new ActionEvent.FinishAttempt(1, AT).occurredAt()).isEqualTo(AT);
        assertThatThrownBy(() -> new ActionEvent.FinishAttempt(0, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionEvent.FinishAttempt(-1, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionEvent.FinishAttempt(1, null))
                .isInstanceOf(NullPointerException.class).hasMessage("occurredAt");
    }

    static Stream<List<Integer>> invalidSteps() {
        return Stream.of(List.of(2), List.of(1, 1), List.of(1, 3), List.of(2, 1));
    }

    @ParameterizedTest
    @MethodSource("invalidSteps")
    void rejectsInvalidOrderWithoutSorting(List<Integer> steps) {
        List<ActionEvent> events = steps.stream()
                .<ActionEvent>map(step -> new ActionEvent.PlaceBlock(1, step, AT)).toList();
        assertThatThrownBy(() -> new ActionTrace(events)).isInstanceOf(IllegalArgumentException.class);
        assertThat(events).extracting(ActionEvent::step).containsExactlyElementsOf(steps);
    }

    @Test
    void rejectsEventsAfterFinishIncludingAnotherFinish() {
        var finish = new ActionEvent.FinishAttempt(1, AT);
        assertThatThrownBy(() -> new ActionTrace(List.of(finish, new ActionEvent.PlaceBlock(1, 2, AT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTrace(List.of(finish, new ActionEvent.FinishAttempt(2, AT))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesTheTraceAndRejectsNulls() {
        var first = new ActionEvent.PlaceBlock(6, 1, AT);
        List<ActionEvent> source = new ArrayList<>(List.of(first));
        var trace = new ActionTrace(source);
        source.clear();

        assertThat(trace.events()).containsExactly(first);
        assertThatThrownBy(() -> trace.events().add(new ActionEvent.FinishAttempt(2, AT)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ActionTrace(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActionTrace(Arrays.asList(first, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ActionReplay.replay(0, null))
                .isInstanceOf(NullPointerException.class).hasMessage("trace");
    }

    @Test
    void replaysBlocksWithExplorationAndExplicitCompletion() {
        var trace = new ActionTrace(List.of(
                new ActionEvent.PlaceBlock(10, 1, AT),
                new ActionEvent.RemoveBlock(4, 2, AT),
                new ActionEvent.FinishAttempt(3, AT)));

        var expected = new ActionReplay.Result(6, true);
        for (int repeat = 0; repeat < 10; repeat++) {
            assertThat(ActionReplay.replay(0, trace)).isEqualTo(expected);
        }
        assertThat(trace.events()).hasSize(3);
    }

    @Test
    void replaysSubtractionFromAnExplicitStartingQuantity() {
        var trace = new ActionTrace(List.of(
                new ActionEvent.RemoveBlock(20, 1, AT),
                new ActionEvent.RemoveBlock(8, 2, AT),
                new ActionEvent.FinishAttempt(3, AT)));
        assertThat(ActionReplay.replay(53, trace)).isEqualTo(new ActionReplay.Result(25, true));
    }

    @Test
    void replaysJumpsByStepRegardlessOfClockChanges() {
        var adjustedClock = new ActionTrace(List.of(
                new ActionEvent.JumpRight(2, 1, AT),
                new ActionEvent.JumpLeft(2, 2, AT),
                new ActionEvent.JumpLeft(2, 3, AT.minusSeconds(1)),
                new ActionEvent.FinishAttempt(4, AT.plusSeconds(1))));
        var increasingClock = new ActionTrace(List.of(
                new ActionEvent.JumpRight(2, 1, AT),
                new ActionEvent.JumpLeft(2, 2, AT.plusSeconds(1)),
                new ActionEvent.JumpLeft(2, 3, AT.plusSeconds(2)),
                new ActionEvent.FinishAttempt(4, AT.plusSeconds(3))));

        assertThat(ActionReplay.replay(28, adjustedClock)).isEqualTo(new ActionReplay.Result(26, true));
        assertThat(ActionReplay.replay(28, increasingClock)).isEqualTo(ActionReplay.replay(28, adjustedClock));
    }

    @Test
    void allowsNegativeNumberLinePositionsAndRoundTrips() {
        var trace = new ActionTrace(List.of(
                new ActionEvent.JumpLeft(2, 1, AT),
                new ActionEvent.JumpRight(2, 2, AT)));
        assertThat(ActionReplay.replay(0, new ActionTrace(trace.events().subList(0, 1))))
                .isEqualTo(new ActionReplay.Result(-2, false));
        assertThat(ActionReplay.replay(0, trace)).isEqualTo(new ActionReplay.Result(0, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 28, -2, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void emptyAndImmediatelyFinishedTracesPreserveTheInitialValue(int initialValue) {
        assertThat(ActionReplay.replay(initialValue, new ActionTrace(List.of())))
                .isEqualTo(new ActionReplay.Result(initialValue, false));
        assertThat(ActionReplay.replay(initialValue,
                new ActionTrace(List.of(new ActionEvent.FinishAttempt(1, AT)))))
                .isEqualTo(new ActionReplay.Result(initialValue, true));
    }

    @Test
    void unfinishedMovementsRemainIncomplete() {
        assertThat(ActionReplay.replay(0,
                new ActionTrace(List.of(new ActionEvent.PlaceBlock(6, 1, AT)))))
                .isEqualTo(new ActionReplay.Result(6, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 5})
    void rejectsRemovingMoreThanAvailable(int initialValue) {
        var trace = new ActionTrace(List.of(new ActionEvent.RemoveBlock(6, 1, AT)));
        assertThatThrownBy(() -> ActionReplay.replay(initialValue, trace))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canRemoveExactlyTheAvailableQuantity() {
        assertThat(ActionReplay.replay(6,
                new ActionTrace(List.of(new ActionEvent.RemoveBlock(6, 1, AT)))))
                .isEqualTo(new ActionReplay.Result(0, false));
    }

    static Stream<Arguments> overflows() {
        return Stream.of(
                Arguments.of(Integer.MAX_VALUE, new ActionEvent.PlaceBlock(1, 1, AT)),
                Arguments.of(Integer.MAX_VALUE, new ActionEvent.JumpRight(1, 1, AT)),
                Arguments.of(Integer.MIN_VALUE, new ActionEvent.JumpLeft(1, 1, AT)));
    }

    @ParameterizedTest
    @MethodSource("overflows")
    void rejectsIntegerOverflow(int initialValue, ActionEvent event) {
        assertThatThrownBy(() -> ActionReplay.replay(initialValue, new ActionTrace(List.of(event))))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void aLaterFailureDoesNotMutateTheTraceOrAffectAnotherReplay() {
        var trace = new ActionTrace(List.of(
                new ActionEvent.PlaceBlock(1, 1, AT),
                new ActionEvent.RemoveBlock(2, 2, AT)));
        var snapshot = List.copyOf(trace.events());

        assertThatThrownBy(() -> ActionReplay.replay(0, trace)).isInstanceOf(IllegalArgumentException.class);
        assertThat(trace.events()).isEqualTo(snapshot);
        assertThat(ActionReplay.replay(1, trace)).isEqualTo(new ActionReplay.Result(0, false));
    }
}
