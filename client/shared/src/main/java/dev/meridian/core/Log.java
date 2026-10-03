package dev.meridian.core;

/**
 * Minimal logging facade. Shared code cannot depend on log4j/slf4j because the available
 * logging stack differs between Minecraft versions, so each adapter installs a {@link Sink}
 * that forwards to the game's logger. Messages use slf4j-style {@code {}} placeholders.
 */
public final class Log {

    /** Destination for log messages; implemented by the version adapter. */
    public interface Sink {
        void info(String message);

        void warn(String message, Throwable error);

        void error(String message, Throwable error);
    }

    private static volatile Sink sink = new StdSink();

    private Log() {
    }

    public static void setSink(Sink newSink) {
        sink = newSink == null ? new StdSink() : newSink;
    }

    public static void info(String message, Object... args) {
        sink.info(format(message, args));
    }

    public static void warn(String message, Object... args) {
        sink.warn(format(message, args), trailingThrowable(args));
    }

    public static void error(String message, Object... args) {
        sink.error(format(message, args), trailingThrowable(args));
    }

    private static Throwable trailingThrowable(Object[] args) {
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
            return (Throwable) args[args.length - 1];
        }
        return null;
    }

    static String format(String message, Object[] args) {
        if (args == null || args.length == 0 || message.indexOf('{') < 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 32);
        int argIndex = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}' && argIndex < args.length) {
                out.append(args[argIndex++]);
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final class StdSink implements Sink {
        @Override
        public void info(String message) {
            System.out.println("[Meridian] " + message);
        }

        @Override
        public void warn(String message, Throwable error) {
            System.out.println("[Meridian/WARN] " + message);
            if (error != null) {
                error.printStackTrace(System.out);
            }
        }

        @Override
        public void error(String message, Throwable error) {
            System.err.println("[Meridian/ERROR] " + message);
            if (error != null) {
                error.printStackTrace();
            }
        }
    }
}
