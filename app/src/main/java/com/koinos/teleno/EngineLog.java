package com.koinos.teleno;

import java.util.regex.Pattern;

/** Classifies native log headers, never words in a message or cumulative counters. */
final class EngineLog {
    private static final Pattern COLOR = Pattern.compile("\u001b\\[[0-9;]*m");
    // koinos-log-cpp console header, with optional datetime and ANSI-colored severity.
    private static final Pattern KOINOS_ERROR = Pattern.compile(
        "^(?:[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6} )?"
        + "\\([^()\\r\\n]+\\) \\[[^\\]\\r\\n]+:[0-9]+\\] <(?:error|fatal)>: ");
    // libp2p's Soralog fallback sink uses datetime, severity, logger, message.
    // Its default has no thread column; a mention of ERROR in the message is not severity.
    private static final Pattern P2P_ERROR = Pattern.compile(
        "^[0-9]{2}\\.[0-9]{2}\\.[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}  (?:ERROR|CRITICAL) +\\S");

    private EngineLog() { }

    static boolean isError(String line) {
        if (line == null || line.isEmpty()) return false;
        String plain = COLOR.matcher(line).replaceAll("");
        // main.cpp writes fatal startup/uncaught exceptions directly to stderr.
        return plain.startsWith("Fatal: ") || KOINOS_ERROR.matcher(plain).find()
            || P2P_ERROR.matcher(plain).find();
    }
}
