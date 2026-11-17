package com.koinos.teleno;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class StartupPortsTests {
    static int assertions;
    static void check(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
    static final class Clock implements StartupPorts.Clock {
        long elapsed; int pauses;
        public long now() { return elapsed; }
        public void pause(long millis) { elapsed += millis; pauses++; }
    }
    interface Action { void run() throws Exception; }
    static IOException fails(Action action) throws Exception {
        try { action.run(); } catch (IOException e) { return e; }
        throw new AssertionError("Expected startup to stop before launch");
    }
    static ServerSocket listen(String host) throws Exception {
        ServerSocket socket = new ServerSocket(); socket.setReuseAddress(true); socket.bind(new InetSocketAddress(host, 0)); return socket;
    }
    static StartupPorts.Endpoint timeWait(String label, String host) throws Exception {
        try (ServerSocket listener = listen(host);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            client.setSoTimeout(2000); accepted.setSoTimeout(2000);
            accepted.shutdownOutput(); check(client.getInputStream().read() == -1, label + " server closes first");
            client.shutdownOutput(); check(accepted.getInputStream().read() == -1, label + " client completes close");
            return new StartupPorts.Endpoint(label, host, listener.getLocalPort());
        }
    }
    public static void main(String[] args) throws Exception {
        StartupPorts.Endpoint rpc = new StartupPorts.Endpoint("Local RPC", "127.0.0.1", 18085);
        StartupPorts.Endpoint p2p = new StartupPorts.Endpoint("Peer network", "0.0.0.0", 18888);
        StartupPorts.Endpoint[] ports = {rpc, p2p};
        Clock clock = new Clock(); AtomicInteger probes = new AtomicInteger();
        check(StartupPorts.await(ports, 5000, () -> false, (names, seconds) -> { throw new AssertionError("No wait needed"); }, p -> probes.incrementAndGet(), clock), "Immediately free listeners allow startup");
        check(probes.get() == 2 && clock.pauses == 0, "Both listeners checked without delay");
        Clock cooldown = new Clock(); AtomicInteger updates = new AtomicInteger();
        check(StartupPorts.await(ports, 5000, () -> false, (names, seconds) -> { updates.incrementAndGet(); check(names.contains("Local RPC") && names.contains("Peer network") && seconds > 0, "Progress names both reserved ports and shows countdown"); }, p -> { if (cooldown.elapsed < 2000) throw new BindException("Address already in use"); }, cooldown), "Temporary reservations continue automatically once free");
        check(cooldown.elapsed == 2000 && updates.get() == 2, "Wait stops as soon as ports become free");
        Clock occupied = new Clock();
        IOException timeout = fails(() -> StartupPorts.await(ports, 2500, () -> false, (names, seconds) -> {}, p -> { throw new BindException("busy"); }, occupied));
        check(occupied.elapsed == 2500, "Persistent conflict has a bounded deadline");
        check(timeout.getMessage().contains("18085") && timeout.getMessage().contains("18888") && timeout.getMessage().contains("wallet registration"), "Timeout identifies network ports and preserves wallet setup");
        Clock noWait = new Clock();
        check(!StartupPorts.await(ports, 5000, () -> true, (n, s) -> {}, p -> { throw new AssertionError("Cancelled request probed"); }, noWait), "Cancelled start never probes or continues");
        AtomicBoolean cancelled = new AtomicBoolean(); Clock cancelledClock = new Clock();
        check(!StartupPorts.await(ports, 5000, cancelled::get, (n, s) -> cancelled.set(true), p -> { throw new BindException("busy"); }, cancelledClock), "Cancel during waiting stops this startup");
        check(cancelledClock.pauses == 0, "Cancellation is checked before sleeping");
        AtomicBoolean cancelAtProbe = new AtomicBoolean();
        check(!StartupPorts.await(new StartupPorts.Endpoint[]{rpc}, 5000, cancelAtProbe::get, (n,s)->{}, p -> cancelAtProbe.set(true), new Clock()), "Cancellation during final successful probe cannot start a node");
        Clock denied = new Clock();
        IOException permission = fails(() -> StartupPorts.await(ports, 5000, () -> false, (n,s)->{}, p -> { throw new IOException("Permission denied"); }, denied));
        check(permission.getMessage().equals("Permission denied") && denied.pauses == 0, "Non-bind failures are not hidden by retry");
        check(!StartupPorts.await(ports, 5000, () -> false, (n,s)->{}, p -> { throw new BindException("busy"); }, new StartupPorts.Clock() {
            public long now() { return 0; }
            public void pause(long ms) throws InterruptedException { throw new InterruptedException(); }
        }), "Interrupted wait cannot start a node");
        check(Thread.interrupted(), "Interruption status preserved for caller");

        try (ServerSocket listener = listen("127.0.0.1")) {
            StartupPorts.Endpoint active = new StartupPorts.Endpoint("Active fixture", "127.0.0.1", listener.getLocalPort());
            IOException blocked = fails(() -> StartupPorts.await(new StartupPorts.Endpoint[]{active}, 0, () -> false, (n,s)->{}, StartupPorts::check, new Clock()));
            check(blocked.getMessage().contains("still in use"), "Actual existing listener is never treated as free");
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort()); Socket accepted = listener.accept()) {
                check(client.isConnected() && accepted.isConnected(), "Existing listener remains available after rejected start");
            }
        }

        // Real TCP regression: both old observer listeners are closed, but strict
        // exclusive binds still fail until TIME_WAIT expires (normally ~60 s).
        StartupPorts.Endpoint[] closed = {timeWait("Local RPC", "127.0.0.1"), timeWait("Peer network", "0.0.0.0")};
        for (StartupPorts.Endpoint endpoint : closed) check(fails(() -> StartupPorts.check(endpoint)) instanceof BindException, "Old immediate check reproduces address-in-use after closing " + endpoint.label);
        AtomicInteger realUpdates = new AtomicInteger(); long began = System.nanoTime();
        System.out.println("RUNNING: real closed TCP connections remain reserved; testing automatic RPC/P2P handoff (up to 90 seconds). System.out is captured in the host log.");
        check(StartupPorts.await(closed, () -> false, (names, remaining) -> realUpdates.incrementAndGet()), "Real RPC and P2P reservations expire and handoff continues");
        check(realUpdates.get() > 0, "Real port cooldown produced visible wait progress");
        // These held exclusive listeners represent the next process's binds;
        // no blockchain engine, wallet or production key is involved in this test.
        try (ServerSocket nextRpc = new ServerSocket(); ServerSocket nextP2p = new ServerSocket()) {
            nextRpc.setReuseAddress(false); nextP2p.setReuseAddress(false);
            nextRpc.bind(new InetSocketAddress(closed[0].host, closed[0].port));
            nextP2p.bind(new InetSocketAddress(closed[1].host, closed[1].port));
            check(nextRpc.isBound() && nextP2p.isBound(), "Both replacement listeners bind after the same gate used by the app");
        }
        System.out.println("Real closed-connection handoff completed in " + ((System.nanoTime() - began) / 1000000000L) + " seconds.");
        System.out.println("PASS: " + assertions + " startup-port, cancellation and real TCP handoff assertions (host Linux; Android runtime testing is separate).");
    }
}
