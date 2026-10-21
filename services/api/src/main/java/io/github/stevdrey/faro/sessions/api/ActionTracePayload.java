// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.stevdrey.faro.sessions.ActionEvent;
import io.github.stevdrey.faro.sessions.ActionTrace;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Version 1 transport contract. The caller supplies the starting quantity explicitly. */
public record ActionTracePayload(int schemaVersion, int initialValue, List<Event> events) {
    public static final int SCHEMA_VERSION = 1;

    public ActionTracePayload {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported action schema version");
        }
        events = List.copyOf(events);
        trace(events); // Validate ordering and finalization at the transport boundary too.
    }

    public ActionTrace toDomain() {
        return trace(events);
    }

    public static ActionTracePayload from(int initialValue, ActionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new ActionTracePayload(SCHEMA_VERSION, initialValue,
                trace.events().stream().map(Event::from).toList());
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PlaceBlock.class, name = "placeBlock"),
        @JsonSubTypes.Type(value = RemoveBlock.class, name = "removeBlock"),
        @JsonSubTypes.Type(value = JumpLeft.class, name = "jumpLeft"),
        @JsonSubTypes.Type(value = JumpRight.class, name = "jumpRight"),
        @JsonSubTypes.Type(value = FinishAttempt.class, name = "finishAttempt")
    })
    public sealed interface Event {
        int step();
        String occurredAt();
        ActionEvent toDomain();

        static Event from(ActionEvent event) {
            String at = event.occurredAt().toString();
            return switch (event) {
                case ActionEvent.PlaceBlock e -> new PlaceBlock(e.value(), e.step(), at);
                case ActionEvent.RemoveBlock e -> new RemoveBlock(e.value(), e.step(), at);
                case ActionEvent.JumpLeft e -> new JumpLeft(e.value(), e.step(), at);
                case ActionEvent.JumpRight e -> new JumpRight(e.value(), e.step(), at);
                case ActionEvent.FinishAttempt e -> new FinishAttempt(e.step(), at);
            };
        }
    }

    public record PlaceBlock(int value, int step, String occurredAt) implements Event {
        public PlaceBlock {
            new ActionEvent.PlaceBlock(value, step, timestamp(occurredAt));
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.PlaceBlock(value, step, timestamp(occurredAt));
        }
    }

    public record RemoveBlock(int value, int step, String occurredAt) implements Event {
        public RemoveBlock {
            new ActionEvent.RemoveBlock(value, step, timestamp(occurredAt));
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.RemoveBlock(value, step, timestamp(occurredAt));
        }
    }

    public record JumpLeft(int value, int step, String occurredAt) implements Event {
        public JumpLeft {
            new ActionEvent.JumpLeft(value, step, timestamp(occurredAt));
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.JumpLeft(value, step, timestamp(occurredAt));
        }
    }

    public record JumpRight(int value, int step, String occurredAt) implements Event {
        public JumpRight {
            new ActionEvent.JumpRight(value, step, timestamp(occurredAt));
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.JumpRight(value, step, timestamp(occurredAt));
        }
    }

    public record FinishAttempt(int step, String occurredAt) implements Event {
        public FinishAttempt {
            new ActionEvent.FinishAttempt(step, timestamp(occurredAt));
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.FinishAttempt(step, timestamp(occurredAt));
        }
    }

    private static ActionTrace trace(List<Event> events) {
        return new ActionTrace(events.stream().map(Event::toDomain).toList());
    }

    private static Instant timestamp(String occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!occurredAt.endsWith("Z")) {
            throw new IllegalArgumentException("occurredAt must be an ISO 8601 UTC timestamp ending in Z");
        }
        return Instant.parse(occurredAt);
    }
}
