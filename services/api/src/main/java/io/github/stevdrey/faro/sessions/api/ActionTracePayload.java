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

/** Version 1 transport contract. The caller supplies the starting quantity explicitly. */
public record ActionTracePayload(int schemaVersion, int initialValue, List<Event> events) {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern UTC_TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z");

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

    // Legacy records are shared with v2, so both versions keep one wire shape and validation path.
    public record PlaceBlock(int value, int step, String occurredAt) implements Event, ActionTracePayloadV2.Event {
        public PlaceBlock {
            Instant at = timestamp(occurredAt);
            new ActionEvent.PlaceBlock(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.PlaceBlock(value, step, Instant.parse(occurredAt));
        }
    }

    public record RemoveBlock(int value, int step, String occurredAt) implements Event, ActionTracePayloadV2.Event {
        public RemoveBlock {
            Instant at = timestamp(occurredAt);
            new ActionEvent.RemoveBlock(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.RemoveBlock(value, step, Instant.parse(occurredAt));
        }
    }

    public record JumpLeft(int value, int step, String occurredAt) implements Event, ActionTracePayloadV2.Event {
        public JumpLeft {
            Instant at = timestamp(occurredAt);
            new ActionEvent.JumpLeft(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.JumpLeft(value, step, Instant.parse(occurredAt));
        }
    }

    public record JumpRight(int value, int step, String occurredAt) implements Event, ActionTracePayloadV2.Event {
        public JumpRight {
            Instant at = timestamp(occurredAt);
            new ActionEvent.JumpRight(value, step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.JumpRight(value, step, Instant.parse(occurredAt));
        }
    }

    public record FinishAttempt(int step, String occurredAt) implements Event, ActionTracePayloadV2.Event {
        public FinishAttempt {
            Instant at = timestamp(occurredAt);
            new ActionEvent.FinishAttempt(step, at);
            occurredAt = at.toString();
        }

        @Override
        public ActionEvent toDomain() {
            return new ActionEvent.FinishAttempt(step, Instant.parse(occurredAt));
        }
    }

    private static ActionTrace trace(List<Event> events) {
        return new ActionTrace(events.stream().map(Event::toDomain).toList());
    }

    /**
     * Strict wire parsing shared by every contract version. Instant.parse is case-insensitive and
     * accepts 24:00, leap seconds and extended years, so it is used only on already-canonical values.
     */
    static Instant timestamp(String occurredAt) {
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
