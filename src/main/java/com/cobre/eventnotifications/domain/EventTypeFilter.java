package com.cobre.eventnotifications.domain;

import java.util.List;

/**
 * The set of event types a {@link Subscription} is interested in. A filter is one or more patterns,
 * each of which is one of:
 *
 * <ul>
 *   <li>{@code "all"} — matches every event type;
 *   <li>an explicit type, e.g. {@code "credit_transfer"} — matches that type exactly;
 *   <li>a prefix pattern ending in {@code *}, e.g. {@code "credit_*"} — matches by prefix.
 * </ul>
 */
public record EventTypeFilter(List<String> patterns) {

    private static final String ALL = "all";

    public EventTypeFilter {
        if (patterns == null || patterns.isEmpty()) {
            throw new IllegalArgumentException("eventTypeFilter must have at least one pattern");
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("eventTypeFilter patterns must not be blank");
            }
        }
        patterns = List.copyOf(patterns);
    }

    /** A filter that matches every event type. */
    public static EventTypeFilter all() {
        return new EventTypeFilter(List.of(ALL));
    }

    public boolean matches(EventType eventType) {
        String type = eventType.value();
        for (String pattern : patterns) {
            if (ALL.equalsIgnoreCase(pattern)) {
                return true;
            }
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (type.startsWith(prefix)) {
                    return true;
                }
            } else if (pattern.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
