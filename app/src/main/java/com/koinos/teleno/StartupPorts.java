package com.koinos.teleno;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/** Match native preflight's exclusive bind; wait for closed TCP connections to expire. */
final class StartupPorts {
    static final long RELEASE_TIMEOUT_MS = 90000;
    static final class Endpoint {
        final String label, host;
        final int port;
        Endpoint(String label, String host, int port) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("A fixed local TCP port is required.");
            this.label = label; this.host = host; this.port = port;
        }
        String description() { return label + " " + host + ":" + port; }
    }
    interface Cancel { boolean requested(); }
    interface Progress { void waiting(String ports, long remainingSeconds) throws Exception; }
    interface Probe { void check(Endpoint endpoint) throws IOException; }
    interface Clock { long now(); void pause(long millis) throws InterruptedException; }

    static void check(Endpoint endpoint) throws IOException {
        // Keep exclusive binding: native preflight intentionally does not use
        // SO_REUSEADDR either. Changing just the Java check hides the first
        // failure but leaves the native RPC/P2P preflight failure in place.
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(endpoint.host, endpoint.port));
        } catch (BindException busy) { throw busy; }
        catch (IOException error) { throw new IOException("Could not check local network port " + endpoint.description() + ". " + error.getMessage(), error); }
    }
    static boolean await(Endpoint[] ports, Cancel cancel, Progress progress) throws Exception {
        return await(ports, RELEASE_TIMEOUT_MS, cancel, progress, StartupPorts::check, new Clock() {
            public long now() { return System.nanoTime() / 1000000L; }
            public void pause(long millis) throws InterruptedException { Thread.sleep(millis); }
        });
    }
    static boolean await(Endpoint[] ports, long timeout, Cancel cancel, Progress progress, Probe probe, Clock clock) throws Exception {
        if (ports.length == 0 || timeout < 0 || timeout > RELEASE_TIMEOUT_MS) throw new IllegalArgumentException("Invalid startup port wait.");
        long start = clock.now();
        while (true) {
            if (cancel.requested()) return false;
            List<String> busy = new ArrayList<>();
            for (Endpoint port : ports) {
                if (cancel.requested()) return false;
                try { probe.check(port); } catch (BindException e) { busy.add(port.description()); }
            }
            if (cancel.requested()) return false;
            if (busy.isEmpty()) return true;
            String names = String.join(", ", busy);
            long remaining = timeout - (clock.now() - start);
            if (remaining <= 0) throw new IOException("Local network port still in use: " + names
                + ". Startup stopped after waiting for old connections to close. Stop any other local node or restart the phone, then start an observer again. Your wallet registration, signing key and chain data are preserved.");
            progress.waiting(names, (remaining + 999) / 1000);
            if (cancel.requested()) return false;
            try { clock.pause(Math.min(1000, remaining)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
    }
}
