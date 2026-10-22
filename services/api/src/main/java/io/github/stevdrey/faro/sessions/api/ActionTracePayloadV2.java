// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.stevdrey.faro.sessions.ActionEvent;
import io.github.stevdrey.faro.sessions.ActionTrace;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Version 2 transport contract. The caller supplies the starting quantity explicitly. */
public record ActionTracePayloadV2(int schemaVersion, int initialValue, List<Event> events) {
    public static final int SCHEMA_VERSION = 2;
    private static final Pattern UTC_TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z");

    public ActionTracePayloadV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported action schema version");
        }
        events = List.copyOf(events);
        trace(events); // Validate ordering and finalization at the transport boundary too.
    }

    public ActionTrace toDomain() {
        return trace(events);
    }

    public static ActionTracePayloadV2 from(int initialValue, ActionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new ActionTracePayloadV2(SCHEMA_VERSION, initialValue,
                trace.events().stream().map(Event::from).toList());
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PlaceBlock.class, name = "placeBlock"),
        @JsonSubTypes.Type(value = RemoveBlock.class, name = "removeBlock"),
        @JsonSubTypes.Type(value = JumpLeft.class, name = "jumpLeft"),
        @JsonSubTypes.Type(value = JumpRight.class, name = "jumpRight"),
        @JsonSubTypes.Type(value = DecomposeTen.class, name = "decomposeTen"),
        @JsonSubTypes.Type(value = ComposeTen.class, name = "composeTen"),
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
                case ActionEvent.DecomposeTen e -> new DecomposeTen(e.step(), at);
                case ActionEvent.ComposeTen e -> new ComposeTen(e.step(), at);
                case ActionEvent.FinishAttempt e -> new FinishAttempt(e.step(), at);
            };
        }
    }

    public record PlaceBlock(int value, int step, String occurredAt) implements Event {
        public PlaceBlock {
            Instant at = timestamp(occurredAt);
            new ActionEvent.PlaceBlock(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.PlaceBlock(value, step, timestamp(occurredAt));
        }
    }

    public record RemoveBlock(int value, int step, String occurredAt) implements Event {
        public RemoveBlock {
            Instant at = timestamp(occurredAt);
            new ActionEvent.RemoveBlock(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.RemoveBlock(value, step, timestamp(occurredAt));
        }
    }

    public record JumpLeft(int value, int step, String occurredAt) implements Event {
        public JumpLeft {
            Instant at = timestamp(occurredAt);
            new ActionEvent.JumpLeft(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.JumpLeft(value, step, timestamp(occurredAt));
        }
    }

    public record JumpRight(int value, int step, String occurredAt) implements Event {
        public JumpRight {
            Instant at = timestamp(occurredAt);
            new ActionEvent.JumpRight(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.JumpRight(value, step, timestamp(occurredAt));
        }
    }

    public record FinishAttempt(int step, String occurredAt) implements Event {
        public FinishAttempt {
            Instant at = timestamp(occurredAt);
            new ActionEvent.FinishAttempt(step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.FinishAttempt(step, timestamp(occurredAt));
        }
    }

    public record DecomposeTen(int step, String occurredAt) implements Event {
        public DecomposeTen {
            Instant at = timestamp(occurredAt);
            new ActionEvent.DecomposeTen(step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() { return new ActionEvent.DecomposeTen(step, timestamp(occurredAt)); }
    }

    public record ComposeTen(int step, String occurredAt) implements Event {
        public ComposeTen {
            Instant at = timestamp(occurredAt);
            new ActionEvent.ComposeTen(step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() { return new ActionEvent.ComposeTen(step, timestamp(occurredAt)); }
    }

    private static ActionTrace trace(List<Event> events) {
        return new ActionTrace(events.stream().map(Event::toDomain).toList());
    }

    /** Instant.parse is case-insensitive and accepts 24:00, leap seconds and extended years. */
    private static Instant timestamp(String occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!UTC_TIMESTAMP.matcher(occurredAt).matches()) {
            throw new IllegalArgumentException("occurredAt must be an ISO 8601 UTC timestamp ending in Z");
        }
        try {
            String local = occurredAt.substring(0, occurredAt.length() - 1);
            return LocalDateTime.parse(local).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("occurredAt is not a valid UTC timestamp", e);
        }
    }
}
