// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.stevdrey.faro.sessions.ActionEvent;
import io.github.stevdrey.faro.sessions.ExchangeEvent;
import io.github.stevdrey.faro.sessions.ExtendedActionTrace;
import io.github.stevdrey.faro.sessions.TraceEvent;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Version 2 transport contract. The caller supplies a nonnegative starting quantity explicitly.
 * Legacy events reuse the v1 records; only the exchanges are new.
 */
public record ActionTracePayloadV2(int schemaVersion, int initialValue, List<Event> events) {
    public static final int SCHEMA_VERSION = 2;

    public ActionTracePayloadV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported action schema version");
        }
        if (initialValue < 0) {
            throw new IllegalArgumentException("Initial quantity must be nonnegative");
        }
        events = List.copyOf(events);
        trace(events); // Validate ordering and finalization at the transport boundary too.
    }

    public ExtendedActionTrace toDomain() {
        return trace(events);
    }

    public static ActionTracePayloadV2 from(int initialValue, ExtendedActionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new ActionTracePayloadV2(SCHEMA_VERSION, initialValue,
                trace.events().stream().map(Event::from).toList());
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ActionTracePayload.PlaceBlock.class, name = "placeBlock"),
        @JsonSubTypes.Type(value = ActionTracePayload.RemoveBlock.class, name = "removeBlock"),
        @JsonSubTypes.Type(value = ActionTracePayload.JumpLeft.class, name = "jumpLeft"),
        @JsonSubTypes.Type(value = ActionTracePayload.JumpRight.class, name = "jumpRight"),
        @JsonSubTypes.Type(value = DecomposeTen.class, name = "decomposeTen"),
        @JsonSubTypes.Type(value = ComposeTen.class, name = "composeTen"),
        @JsonSubTypes.Type(value = ActionTracePayload.FinishAttempt.class, name = "finishAttempt")
    })
    public sealed interface Event permits ActionTracePayload.PlaceBlock, ActionTracePayload.RemoveBlock,
            ActionTracePayload.JumpLeft, ActionTracePayload.JumpRight, ActionTracePayload.FinishAttempt,
            DecomposeTen, ComposeTen {
        int step();
        String occurredAt();
        TraceEvent toDomain();

        static Event from(TraceEvent event) {
            return switch (event) {
                // Every legacy v1 record also implements this interface.
                case ActionEvent e -> (Event) ActionTracePayload.Event.from(e);
                case ExchangeEvent.DecomposeTen e -> new DecomposeTen(e.step(), e.occurredAt().toString());
                case ExchangeEvent.ComposeTen e -> new ComposeTen(e.step(), e.occurredAt().toString());
            };
        }
    }

    public record DecomposeTen(int step, String occurredAt) implements Event {
        public DecomposeTen {
            Instant at = ActionTracePayload.timestamp(occurredAt);
            new ExchangeEvent.DecomposeTen(step, at);
            occurredAt = at.toString();
        }

        @Override
        public TraceEvent toDomain() { return new ExchangeEvent.DecomposeTen(step, Instant.parse(occurredAt)); }
    }

    public record ComposeTen(int step, String occurredAt) implements Event {
        public ComposeTen {
            Instant at = ActionTracePayload.timestamp(occurredAt);
            new ExchangeEvent.ComposeTen(step, at);
            occurredAt = at.toString();
        }

        @Override
        public TraceEvent toDomain() { return new ExchangeEvent.ComposeTen(step, Instant.parse(occurredAt)); }
    }

    private static ExtendedActionTrace trace(List<Event> events) {
        return new ExtendedActionTrace(events.stream().map(Event::toDomain).toList());
    }
}
