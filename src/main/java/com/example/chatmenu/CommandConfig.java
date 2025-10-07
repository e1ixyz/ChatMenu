package com.example.chatmenu;

import java.util.List;
import java.util.Locale;

public class CommandConfig {
    public final String name;        // command name (lowercase)
    public final String permission;  // permission required to open the menu
    public final CommandType type;   // SELF or TARGET
    public final List<Line> lines;

    public CommandConfig(String name, String permission, CommandType type, List<Line> lines) {
        this.name = name;
        this.permission = permission == null ? "" : permission;
        this.type = type == null ? CommandType.SELF : type;
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public enum PapiContext { VIEWER, TARGET;
        public static PapiContext fromString(Object value, PapiContext fallback) {
            if (value == null) return fallback;
            String s = value.toString().trim().toLowerCase(Locale.ROOT);
            if (s.equals("target")) return TARGET;
            if (s.equals("viewer")) return VIEWER;
            return fallback;
        }
    }

    public static final class Line {
        public final List<Segment> segments;

        public Line(List<Segment> segments) {
            this.segments = segments == null ? List.of() : List.copyOf(segments);
        }

        public boolean isEmpty() {
            return segments.isEmpty();
        }
    }

    public interface Segment {
        PapiContext context();
    }

    public static final class TextSegment implements Segment {
        public final String text;
        private final PapiContext context;

        public TextSegment(String text, PapiContext context) {
            this.text = text == null ? "" : text;
            this.context = context == null ? PapiContext.VIEWER : context;
        }

        @Override
        public PapiContext context() {
            return context;
        }
    }

    public static final class ButtonSegment implements Segment {
        public final String display;
        public final String hover;
        public final List<String> commands;
        public final boolean defaultAsPlayer;
        public final boolean appendSpace;
        private final PapiContext context;

        public ButtonSegment(String display, String hover, List<String> commands,
                             boolean defaultAsPlayer, boolean appendSpace, PapiContext context) {
            this.display = display == null ? "" : display;
            this.hover = hover == null ? "" : hover;
            this.commands = commands == null ? List.of() : List.copyOf(commands);
            this.defaultAsPlayer = defaultAsPlayer;
            this.appendSpace = appendSpace;
            this.context = context == null ? PapiContext.VIEWER : context;
        }

        @Override
        public PapiContext context() {
            return context;
        }
    }
}
