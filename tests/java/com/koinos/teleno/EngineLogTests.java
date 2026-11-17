package com.koinos.teleno;

public final class EngineLogTests {
    static int assertions;
    static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    static String nativeLine(String level, String message) {
        return "2026-01-02 03:04:05.123456 (teleno.Ab123) [main.cpp:2401] <" + level + ">: " + message;
    }
    public static void main(String[] args) {
        String metrics = "[metrics] head_height=101 lib=41 producer_running=1 producer_errors=0 peer_count=3";
        check(!EngineLog.isError(nativeLine("info", metrics)), "Normal producer_errors=0 metrics are not errors");
        check(!EngineLog.isError(nativeLine("\u001b[32minfo\u001b[0m", metrics)), "Real colored native metrics do not trigger an alert");
        check(!EngineLog.isError(nativeLine("info", metrics.replace("producer_errors=0", "producer_errors=3"))), "Cumulative error count is not a new error log");
        check(!EngineLog.isError(nativeLine("info", "Error counter reset")), "Message text is not severity");
        check(!EngineLog.isError(nativeLine("info", "peer sent <error>: example")), "Error token in an info body ignored");
        check(!EngineLog.isError(nativeLine("warning", "[block_producer] retry after error")), "Recoverable warning does not claim engine error");
        check(!EngineLog.isError("I: All logs will be written to color standard output with 'error' level"), "Logging configuration announcement is not an error");
        check(EngineLog.isError(nativeLine("error", "[chain] Indexer failed; refusing to enter ready state")), "Actual error severity retained without keyword in message");
        check(EngineLog.isError(nativeLine("fatal", "[node] Could not start")), "Fatal severity retained");
        check(EngineLog.isError(nativeLine("\u001b[31merror\u001b[0m", "[chain] Could not open database")), "ANSI-colored error retained");
        check(EngineLog.isError(nativeLine("\u001b[31mfatal\u001b[0m", "[node] Could not start")), "ANSI-colored fatal retained");
        check(EngineLog.isError("(teleno.Ab123) [main.cpp:2326] <error>: Could not open database"), "Datetime-disabled log recognized");
        check(!EngineLog.isError("(teleno.Ab123) [main.cpp:2401] <info>: " + metrics), "Datetime-disabled metrics remain normal");
        check(EngineLog.isError("Fatal: Address already in use"), "Uncaught native startup exception retained");
        check(!EngineLog.isError(nativeLine("info", "peer sent Fatal: bad data")), "Fatal text in info body ignored");
        check(EngineLog.isError("26.01.02 03:04:05.123456  ERROR     Libp2p  Connection failed"), "Actual libp2p error retained");
        check(EngineLog.isError("26.01.02 03:04:05.123456  CRITICAL  Libp2p  Could not start"), "Actual libp2p critical retained");
        check(EngineLog.isError("26.01.02 03:04:05\u001b[38;2;128;128;128m.123456\u001b[0m  \u001b[1;31mERROR   \u001b[0m  Libp2p  Connection failed"), "Colored libp2p header retained");
        check(!EngineLog.isError("26.01.02 03:04:05.123456  INFO      Libp2p  error counter=0"), "Libp2p info message containing error ignored");
        check(!EngineLog.isError("26.01.02 03:04:05.123456  WARNING   Libp2p  ERROR in peer response"), "Libp2p warning body is not severity");
        check(!EngineLog.isError(nativeLine("info", "26.01.02 03:04:05.123456  ERROR     Libp2p  example")), "Embedded log-like body cannot override actual info header");
        check(!EngineLog.isError("producer_errors=0"), "Unscoped metrics do not trigger an alert");
        check(!EngineLog.isError(null) && !EngineLog.isError(""), "Missing output ignored");
        check(!EngineLog.isError("unrecognized output mentioning error"), "Unstructured text is not treated as severity; process exit is checked separately");
        System.out.println("PASS: " + assertions + " native log classification assertions.");
    }
}
