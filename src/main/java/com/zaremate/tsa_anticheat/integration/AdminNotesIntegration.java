package com.zaremate.tsa_anticheat.integration;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional bridge to Admin Notes.
 *
 * <p>TSA remains fully functional when Admin Notes is not installed.</p>
 */
@SuppressWarnings("null")
public final class AdminNotesIntegration {
    private static final String API_CLASS =
            "com.zaremate.admin_notes.AdminNotesAPI";

    private AdminNotesIntegration() {}

    public static boolean isAvailable() {
        try {
            Class.forName(API_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static List<NoteView> getNotes(UUID playerUuid) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod("getNotes", UUID.class);
            Object value = method.invoke(null, playerUuid);

            if (!(value instanceof List<?> notes)) {
                return List.of();
            }

            return notes.stream()
                    .map(AdminNotesIntegration::toView)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static Optional<NoteView> getNote(UUID playerUuid, UUID noteId) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod(
                    "getNote",
                    UUID.class,
                    UUID.class
            );
            Object value = method.invoke(null, playerUuid, noteId);

            if (!(value instanceof Optional<?> optional)
                    || optional.isEmpty()) {
                return Optional.empty();
            }

            return toView(optional.get());
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static Optional<NoteView> addSystemNote(
            UUID playerUuid,
            String text
    ) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod(
                    "addSystemNote",
                    UUID.class,
                    String.class
            );

            return toView(method.invoke(null, playerUuid, text));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static boolean removeNote(UUID playerUuid, UUID noteId) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod(
                    "removeNote",
                    UUID.class,
                    UUID.class
            );

            return Boolean.TRUE.equals(
                    method.invoke(null, playerUuid, noteId)
            );
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Optional<NoteView> toView(Object note) {
        if (note == null) {
            return Optional.empty();
        }

        try {
            UUID id = (UUID) note.getClass().getMethod("id").invoke(note);
            UUID authorUuid = (UUID) note.getClass()
                    .getMethod("authorUuid")
                    .invoke(note);
            String author = (String) note.getClass()
                    .getMethod("author")
                    .invoke(note);
            String text = (String) note.getClass()
                    .getMethod("text")
                    .invoke(note);
            long createdAt = ((Number) note.getClass()
                    .getMethod("createdAt")
                    .invoke(note))
                    .longValue();

            return Optional.of(
                    new NoteView(id, authorUuid, author, text, createdAt)
            );
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public record NoteView(
            UUID id,
            UUID authorUuid,
            String author,
            String text,
            long createdAt
    ) {
        public boolean isSystem() {
            return authorUuid == null;
        }
    }
}
