package com.koinos.teleno;

import java.lang.Process;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;

public final class NodeService extends Service {
    static final String OBSERVER = "observer", PRODUCER = "producer", STOP = "stop", FORCE = "force";
    static final String MOVE_STORAGE = "move-storage", CLEAN_STORAGE = "clean-storage";
    static final String DOWNLOAD_CHAIN="download-chain", DISCARD_DOWNLOAD="discard-download";
    static final String REVIEWED="production-reviewed", REVIEW_ACCOUNT="reviewed-account", REVIEW_KEY="reviewed-public-key";
    private static final String CHANNEL = "local-node";
    private static final int NOTICE = 100;
    private static final Pattern PEERS = Pattern.compile("\\bpeer_count=(\\d+)");
    private static final Object LOCK = new Object();
    private static volatile NodeService instance;
    static final class ViewState {
        String phase = "Stopped", detail = "Start an observer to validate and sync the chain.", mode = "stopped", network = "harbinger";
        long height, lib, freeBytes, headTime, sampleAt, localBlocks, runStartedAt, runEndedAt; boolean producerRunning; int peers = -1;
        ProductionStatus.Snapshot production = new ProductionStatus().snapshot();
        String producerAccount="",runNetwork="",runAccount="";
        boolean active, busy, ready, hasSample, downloading; int downloadPercent=-1;
        String readiness = "Start an observer first.", productionProblem = "", productionProblemNetwork = "";
        List<String> events = new ArrayList<>();
        ViewState copy() {
            ViewState x = new ViewState(); x.phase = phase; x.detail = detail; x.mode = mode; x.network = network;
            x.runStartedAt=runStartedAt;x.runEndedAt=runEndedAt;
            x.runNetwork=runNetwork;x.runAccount=runAccount;
            x.producerAccount=producerAccount;x.production=production;x.sampleAt=sampleAt;x.localBlocks=localBlocks;x.producerRunning=producerRunning; x.height = height; x.lib = lib; x.freeBytes = freeBytes; x.headTime = headTime; x.peers = peers;
            x.active = active; x.busy = busy; x.ready = ready; x.hasSample = hasSample; x.downloading=downloading;x.downloadPercent=downloadPercent;x.readiness = readiness; x.productionProblem = productionProblem; x.productionProblemNetwork = productionProblemNetwork; x.events = new ArrayList<>(events); return x;
        }
    }
    private static final ViewState VIEW = new ViewState();
    static ViewState snapshot() { synchronized (LOCK) { return VIEW.copy(); } }
    static boolean isActive() { synchronized (LOCK) { return VIEW.active || VIEW.busy; } }
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong epoch = new AtomicLong();
    private final Readiness readiness = new Readiness();
    private final ProductionStatus productionStatus = new ProductionStatus();
    private volatile Process process;
    private volatile String network = "harbinger", mode = "stopped";
    private volatile boolean stopRequested, transitioning;
    private volatile String stopReason;
    private PowerManager.WakeLock wake;
    private long wakeAt, startedAt;
    private int readFailures;

    @Override public void onCreate() {
        super.onCreate(); instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Local Teleno node", NotificationManager.IMPORTANCE_LOW));
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "teleno:node"); wake.setReferenceCounted(false);
        monitor.scheduleWithFixedDelay(this::poll, 2, 5, TimeUnit.SECONDS);
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (!Arrays.asList(OBSERVER, PRODUCER, STOP, FORCE, MOVE_STORAGE, CLEAN_STORAGE,DOWNLOAD_CHAIN,DISCARD_DOWNLOAD).contains(action)) return START_NOT_STICKY;
        Notification notification = notification("Preparing local node");
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTICE, notification);
        if (STOP.equals(action) || FORCE.equals(action)) {
            epoch.incrementAndGet(); stopRequested = true; stopReason = null;
            Process p = process; if (p != null) { if (FORCE.equals(action)) p.destroyForcibly(); else p.destroy(); }
            else if(snapshot().busy)status("Cancelling operation","Cancelling the current operation. A network read may take up to 20 seconds to stop. Verified chain data and signing keys are preserved.");
            commands.execute(() -> { try { stopNode(FORCE.equals(action)); } catch (Exception e) { failure(e); } });
            return START_NOT_STICKY;
        }
        synchronized (LOCK) {
            if (VIEW.busy || PRODUCER.equals(mode) || (OBSERVER.equals(action) && process != null)) return START_NOT_STICKY;
            if ((MOVE_STORAGE.equals(action) || CLEAN_STORAGE.equals(action)||DOWNLOAD_CHAIN.equals(action)||DISCARD_DOWNLOAD.equals(action)) && process != null) return START_NOT_STICKY;
            VIEW.busy = true;
            if (PRODUCER.equals(action)) VIEW.productionProblem = "";
        }
        long request = epoch.incrementAndGet(); stopRequested = false; stopReason = null;
        String chosen = intent.getStringExtra("network");
        commands.execute(() -> {
            try {
                NodeConfig.checkNetwork(chosen);
                if(DOWNLOAD_CHAIN.equals(action)) {
                    network=chosen;requireEnvironment(false);verifyEngine(false);
                    wake.acquire(10*60*1000L);wakeAt=System.currentTimeMillis();
                    synchronized(LOCK){VIEW.downloading=true;VIEW.downloadPercent=-1;}
                    final long[] clock={0,0,System.currentTimeMillis(),0,0};final String[] previous={""};final double[] speed={0};
                    SnapshotBootstrap.run(this,chosen,intent.getStringExtra("snapshot_id"),intent.getStringExtra("manifest_hash"),intent.getStringExtra("files_hash"),(phase,done,total,files,count)->{
                        if(epoch.get()!=request||stopRequested)throw new IOException("Download paused by you.");
                        long now=System.currentTimeMillis();
                        if(now-clock[1]>5000){requireEnvironment(false);clock[1]=now;}
                        if(now-wakeAt>5*60*1000L){wake.acquire(10*60*1000L);wakeAt=now;}
                        if(now-clock[0]>700||!phase.equals(previous[0])) {
                            if("Downloading chain snapshot".equals(phase)&&phase.equals(previous[0])&&done>=clock[3]&&now>clock[2]) {
                                double sample=(done-clock[3])*1000.0/(now-clock[2]);speed[0]=speed[0]==0?sample:.75*speed[0]+.25*sample;
                            }else speed[0]=0;
                            clock[0]=clock[2]=now;clock[3]=done;previous[0]=phase;
                            int percent=total==0?-1:(int)Math.min(100,done*100.0/total);
                            String detail=total==0?"Checking the exact snapshot and selected storage.":SnapshotDownload.gib(done)+" / "+SnapshotDownload.gib(total)+" GiB · "+percent+"%\n"+files+" / "+count+" files verified";
                            if(speed[0]>1024){long seconds=(long)((total-done)/speed[0]);detail+=String.format(Locale.ROOT,"\n%.1f MiB/s · about %d min remaining",speed[0]/1048576.0,Math.max(1,(seconds+59)/60));}
                            detail+="\nStorage: "+NodeStorage.label(this,chosen)+". Keep Wi-Fi connected and the card inserted.";
                            synchronized(LOCK){VIEW.downloadPercent=percent;}
                            status(phase,detail);
                            if(now-clock[4]>5000||percent==100){saveDownload(phase+"\n"+detail,percent);clock[4]=now;}
                        }
                    });
                    saveDownload("Snapshot restored and verified. The observer will validate new blocks and catch up; production stays off.",100);
                    synchronized(LOCK){VIEW.downloading=false;}
                    if(epoch.get()==request&&!stopRequested){requireEnvironment(false);launch(false,null,request);}
                } else if(DISCARD_DOWNLOAD.equals(action)) {
                    network=chosen;status("Removing unfinished download","Removing only the inactive download. The current chain and signing keys are preserved.");
                    SnapshotBootstrap.discard(this,chosen);saveDownload("Unfinished download removed. You can check for the latest snapshot or sync from genesis.",-1);
                    status("Download removed","The active chain database and keys were preserved.");
                } else if (MOVE_STORAGE.equals(action) || CLEAN_STORAGE.equals(action)) {
                    if(SnapshotBootstrap.pending(this,chosen))throw new IOException("Resume or discard the unfinished snapshot download before changing storage.");
                    network = chosen; wake.acquire(10 * 60 * 1000L);wakeAt=System.currentTimeMillis();
                    if (CLEAN_STORAGE.equals(action)) { status("Removing previous copy", "Removing only the retained database. Signing keys stay on the phone."); NodeStorage.clean(this,chosen); status("Storage cleanup complete","The verified active database and signing keys were preserved."); }
                    else {
                        final long[] notified={0};
                        status("Preparing storage transfer","Checking the destination. The node remains stopped during the transfer.");
                        NodeStorage.change(this, chosen, intent.getStringExtra("volume"), (copied,total,stage)-> {
                            if(epoch.get()!=request || stopRequested) throw new IOException("Storage transfer cancelled. Original chain data remains selected.");
                            long now=System.currentTimeMillis();if(now-wakeAt>5*60*1000L){wake.acquire(10*60*1000L);wakeAt=now;}
                            if(now-notified[0]>700 || "Copy verified".equals(stage)) {
                                notified[0]=now;
                                status(stage,String.format(Locale.ROOT,"%.2f / %.2f GiB · %d%%\nKeep the SD card inserted. The previous copy is retained until the new observer is validated.",copied/1073741824.0,total/1073741824.0,total==0?100:(int)(copied*100.0/total)));
                            }
                        });
                        status("Storage ready", "Chain storage: " + NodeStorage.label(this,chosen) + ". Start the observer, then stop it and remove the previous copy to free space.");
                    }
                } else if (PRODUCER.equals(action)) startProducer(request, chosen,
                    new ProductionReview(intent.getBooleanExtra(REVIEWED, false), chosen, intent.getStringExtra(REVIEW_ACCOUNT), intent.getByteArrayExtra(REVIEW_KEY)));
                else { network = chosen;if(SnapshotBootstrap.pending(this,chosen))throw new IOException("Resume or discard the unfinished snapshot download before starting the observer.");requireEnvironment(false); verifyEngine(false); launch(false, null, request); }
            } catch (Exception | LinkageError e) {
                if (PRODUCER.equals(action)) synchronized (LOCK) { VIEW.productionProblemNetwork = chosen; VIEW.productionProblem = e.getMessage() == null ? "Production did not start. Review the current node status and try again." : e.getMessage(); }
                if(DOWNLOAD_CHAIN.equals(action)) {
                    String reason=e.getMessage()==null?"Check your connection and selected storage.":e.getMessage();
                    String detail=reason+"\nVerified download files are kept. Tap Resume chain download to continue. If the restore already completed, tap Start observer.";
                    saveDownload(detail,snapshot().downloadPercent);status("Chain download needs attention",detail);
                }else failure(e);
            }
            finally { synchronized (LOCK) { VIEW.busy = false;VIEW.downloading=false; } if (process == null) endService(); }
        });
        return START_NOT_STICKY;
    }
    private void verifyEngine(boolean producer) throws Exception {
        if (!Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")) throw new IllegalStateException("This build needs a 64-bit ARM Android installation.");
        JSONObject info = new JSONObject(new String(NodeConfig.asset(this, "engine.json"), StandardCharsets.UTF_8));
        if (!info.optBoolean("available")) throw new IllegalStateException("The node engine is missing. Install the full Teleno Node app to start the node.");
        if (!NodeConfig.COMMIT.equals(info.getString("sourceCommit")) || !"arm64-v8a".equals(info.getString("abi"))) throw new IllegalStateException("The bundled node identity is incorrect.");
        if (producer && !ProductionReview.buildAllowsProduction(info.optBoolean("available"), info.optBoolean("productionQualified"), info.optBoolean("experimentalProductionEnabled")))
            throw new IllegalStateException("This APK does not enable block production. Install the full Teleno Node app to use step 5.");
        JSONObject artifacts = info.getJSONObject("sha256");
        for (String name : Arrays.asList("libteleno_node.so", "libteleno_runner.so", "libteleno_keys.so")) {
            File f = new File(getApplicationInfo().nativeLibraryDir, name);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(f)) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n); }
            StringBuilder hex = new StringBuilder(); for (byte v : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", v & 255));
            if (!hex.toString().equals(artifacts.getString(name))) throw new IllegalStateException("The installed node failed its integrity check. Reinstall a complete APK.");
        }
    }
    private void requireEnvironment(boolean producer) throws Exception {
        NotificationManager notices = getSystemService(NotificationManager.class);
        NotificationChannel channel = notices.getNotificationChannel(CHANNEL);
        if (!notices.areNotificationsEnabled() || channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) throw new IllegalStateException("Enable Teleno's local-node notifications in Android settings before starting.");
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo(); getSystemService(ActivityManager.class).getMemoryInfo(memory);
        if (memory.lowMemory) throw new IllegalStateException("Android reports low memory. Close other apps and start an observer when memory is available.");
        NodeStorage.checkMounted(this,network);
        long free = NodeStorage.free(this,network);
        synchronized (LOCK) { VIEW.freeBytes = free; }
        if (free < 2L * 1024 * 1024 * 1024) throw new IllegalStateException("Free at least 2 GiB before starting. The full chain needs additional growing storage.");
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc == null || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) throw new IllegalStateException("Connect to an unmetered network, such as Wi-Fi, then start again.");
        if (Build.VERSION.SDK_INT >= 29 && getSystemService(PowerManager.class).getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) throw new IllegalStateException("The device is too hot. Let it cool before restarting the observer.");
        if (producer) {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null || battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) throw new IllegalStateException("Connect power before starting block production.");
        }
    }
    private void startProducer(long request, String chosen, ProductionReview review) throws Exception {
        if (!OBSERVER.equals(mode) || process == null || !process.isAlive() || !chosen.equals(network)) throw new IllegalStateException("Run an observer on this network before enabling production.");
        verifyEngine(true); requireEnvironment(true);
        String account = getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + network, ""); Base58.address(account);
        synchronized (readiness) { String blocker = readiness.blocker(System.currentTimeMillis()); if (blocker != null) throw new IllegalStateException(blocker); }
        LocalRpc rpc = new LocalRpc(); if (!rpc.chainMatches(network)) throw new IllegalStateException("The local node is on a different chain.");
        KeyVault vault = new KeyVault(this, network); byte[] publicKey = vault.publicKey();
        review.requireCurrent(network, account, publicKey);
        rpc.checkProducer(account, publicKey);
        if("mainnet".equals(network)&&account.equals(getSharedPreferences("settings",MODE_PRIVATE).getString("vault-producer-mainnet",""))) {
            ProductionPermission.Snapshot permission=ProductionPermission.read(rpc::call,account,System.currentTimeMillis());
            if(permission.allowance.signum()==0)throw new IllegalStateException("This KOIN Vault producer has no VHP production allowance. Keep the observer running. In Setup step 5, review VHP production permission, approve it in KOIN Vault, then Check VHP permission before starting production.");
        }
        JSONObject head = rpc.call("chain.get_head_info", new JSONObject());
        synchronized (readiness) {
            readiness.sample(head.getJSONObject("head_topology").getLong("height"), head.getLong("last_irreversible_block"), head.getLong("head_block_time"), System.currentTimeMillis(), true);
            String blocker = readiness.blocker(System.currentTimeMillis()); if (blocker != null) throw new IllegalStateException(blocker);
        }
        if (epoch.get() != request || stopRequested) return;
        transitioning = true;
        try {
            status("Switching to production", "Stopping the observer cleanly before enabling the registered signing key.");
            Process previous = process; previous.destroy();
            if (!previous.waitFor(30, TimeUnit.SECONDS)) throw new IllegalStateException("The observer is still shutting down. Use Stop and wait; production has not started.");
            process = null; mode = "stopped";
            getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("unfinished-run", false).commit();
            synchronized (LOCK) { VIEW.active = false; VIEW.ready = false; VIEW.mode = "stopped"; VIEW.readiness = "Observer stopped. Preparing block production."; }
            if (epoch.get() != request || stopRequested) return;
            requireEnvironment(true);
            if (!account.equals(getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + network, ""))) throw new IllegalStateException("The producer account changed. Start the observer and check again.");
            launch(true, review, request);
        } finally { transitioning = false; }
    }
    private void launch(boolean producer, ProductionReview review, long request) throws Exception {
        if (process != null && process.isAlive()) throw new IllegalStateException("A node is already running.");
        if (epoch.get() != request || stopRequested) return;
        if (producer && review == null) throw new IllegalStateException("Review production in step 5 before starting.");
        String account = producer ? review.account : null;
        // Both native listeners can remain reserved by TCP TIME_WAIT after a
        // clean shutdown. Preserve strict checks and wait before key access.
        wake.acquire(10 * 60 * 1000L); wakeAt = System.currentTimeMillis();
        final long[] lastEnvironmentCheck = {wakeAt};
        if (!StartupPorts.await(new StartupPorts.Endpoint[] {
                new StartupPorts.Endpoint("Local RPC", "127.0.0.1", NodeConfig.RPC_PORT),
                new StartupPorts.Endpoint("Peer network", "0.0.0.0", NodeConfig.P2P_PORT)
            }, () -> epoch.get() != request || stopRequested, (ports, remaining) -> {
                long now = System.currentTimeMillis();
                if (now - lastEnvironmentCheck[0] >= 5000) { requireEnvironment(producer); lastEnvironmentCheck[0] = now; }
                status("Waiting for local network ports", "The previous node's connections may still be closing. " + ports
                    + "\nContinuing automatically when free · up to " + remaining + " seconds remaining."
                    + "\nThis is a network port check. Keep your wallet registration and signing key. Cancel operation stops this start attempt.");
            })) return;
        if (epoch.get() != request || stopRequested) return;
        NodeStorage.ensureReady(this,network);
        File config = NodeConfig.prepare(this, network, producer ? account : null);
        File secret = NodeConfig.secretFile(this, network);
        NodeConfig.clearTransientKey(this, network);
        File base = NodeConfig.root(this, network);
        if (!checkStartup(base, config, secret, request)) return;
        requireEnvironment(producer);
        if (producer) review.requireCurrent(network, getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + network, ""), new KeyVault(this, network).publicKey());
        // The pinned Teleno loader accepts /proc/self/fd/0. Pass the WIF through
        // the inherited private stdin pipe, never through argv or a disk file.
        byte[] signingLine = producer ? (new KeyVault(this, network).exportWif() + "\n").getBytes(StandardCharsets.US_ASCII) : null;
        try {
            if (epoch.get() != request || stopRequested) { NodeConfig.clearTransientKey(this, network); return; }
            String libs = getApplicationInfo().nativeLibraryDir;
            ProcessBuilder builder = new ProcessBuilder(libs + "/libteleno_runner.so", new File(base, "node.owner.lock").getPath(), secret.getPath(), libs + "/libteleno_node.so",
                "--basedir", base.getPath(), "--config", config.getPath(), "--jobs", "2", producer ? "--enable" : "--disable", "block_producer", "--disable", "grpc");
            builder.directory(base); builder.redirectErrorStream(true);
            builder.environment().put("TMPDIR", getCacheDir().getPath());
            Process child = builder.start(); process = child; mode = producer ? PRODUCER : OBSERVER;
            getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("unfinished-run", true).putString("unfinished-network", network).commit();
            startedAt = System.currentTimeMillis(); readFailures = 0;
            productionStatus.reset();
            synchronized (readiness) { readiness.reset(); }
            synchronized (LOCK) { VIEW.runStartedAt=producer?startedAt:0;VIEW.runEndedAt=0;VIEW.runNetwork=network;VIEW.runAccount=producer?account:"";VIEW.producerAccount=producer?account:"";VIEW.production=productionStatus.snapshot();VIEW.localBlocks=0;VIEW.producerRunning=false;VIEW.active = true; VIEW.mode = mode; VIEW.network = network; VIEW.ready = false; VIEW.hasSample = false; VIEW.peers = -1; VIEW.height = VIEW.lib = VIEW.headTime = 0; }
            wake.acquire(10 * 60 * 1000L); wakeAt = System.currentTimeMillis();
            try (OutputStream input = child.getOutputStream()) { if (signingLine != null) input.write(signingLine); }
            status("Starting " + (producer ? "producer" : "observer"), "Opening this device's chain database. First startup can take time.");
            Thread logReader = new Thread(() -> readOutput(child), "teleno-output"); logReader.setDaemon(true); logReader.start();
        } catch (Exception e) { if (process == null) NodeConfig.clearTransientKey(this, network); else process.destroy(); throw e; }
        finally { if (signingLine != null) Arrays.fill(signingLine, (byte) 0); }
    }
    private boolean checkStartup(File base, File config, File secret, long request) throws Exception {
        if (epoch.get() != request || stopRequested) return false;
        status("Checking local startup", "Checking the bundled engine, configuration, storage access and listener ports. The database is not opened by this check.");
        String libs = getApplicationInfo().nativeLibraryDir;
        ProcessBuilder builder = new ProcessBuilder(libs + "/libteleno_runner.so", new File(base, "node.owner.lock").getPath(), secret.getPath(), libs + "/libteleno_node.so",
            "--basedir", base.getPath(), "--config", config.getPath(), "--preflight", "--preflight-json", "--disable", "block_producer", "--disable", "grpc");
        builder.directory(base); builder.redirectErrorStream(true); builder.environment().put("TMPDIR", getCacheDir().getPath());
        boolean priorTransition = transitioning; transitioning = true;
        Process check = null;
        try {
            check = builder.start(); process = check; check.getOutputStream().close();
            final Process owned = check;
            FutureTask<byte[]> output = new FutureTask<>(() -> { try (InputStream in = owned.getInputStream()) { return NodeConfig.readBounded(in, 256 * 1024); } });
            Thread reader = new Thread(output, "teleno-preflight"); reader.setDaemon(true); reader.start();
            if (!check.waitFor(30, TimeUnit.SECONDS)) throw new IOException("The local startup check timed out. The chain database was not opened. Restart the app and retry the observer.");
            if (epoch.get() != request || stopRequested) return false;
            if (check.exitValue() == 73) throw new IOException("Another node still owns this database. Wait for it to stop before retrying.");
            JSONObject report;
            try { report = new JSONObject(new String(output.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8)); }
            catch (Exception e) { throw new IOException("Android could not complete the bundled engine's startup check (exit " + check.exitValue() + "). Chain data is preserved. Check node status for details."); }
            if (report.optInt("schema_version") != 1 || !"local-startup-prerequisites".equals(report.optString("scope"))) throw new IOException("The engine returned an unexpected startup report. Reinstall a complete matching APK.");
            if (check.exitValue() != 0 || !report.optBoolean("no_known_blockers")) {
                StringBuilder problems = new StringBuilder("Local startup needs attention:");
                org.json.JSONArray checks = report.getJSONArray("checks");
                for (int i = 0; i < checks.length() && problems.length() < 2500; i++) {
                    JSONObject item = checks.getJSONObject(i); String state = item.optString("status");
                    if ("fail".equals(state) || "error".equals(state)) {
                        String id = item.optString("id");
                        problems.append("\n").append("ports.jsonrpc".equals(id) ? "Local RPC port " + NodeConfig.RPC_PORT + ": " : "ports.p2p".equals(id) ? "Peer network port " + NodeConfig.P2P_PORT + ": " : "")
                            .append(item.optString("observed")).append(" ").append(item.optString("action"));
                    }
                }
                throw new IOException(problems.toString());
            }
            event("Local startup checks passed. Chain identity, sync and production readiness are checked separately after start.");
            return true;
        } finally {
            if (check != null) {
                if (check.isAlive()) { check.destroy(); if (!check.waitFor(2, TimeUnit.SECONDS)) { check.destroyForcibly(); check.waitFor(2, TimeUnit.SECONDS); } }
                if (process == check) process = null;
            }
            transitioning = priorTransition;
        }
    }
    private void readOutput(Process child) {
        try (InputStream in = child.getInputStream()) {
            ByteArrayOutputStream line = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n; boolean oversized = false;
            while ((n = in.read(buffer)) != -1) for (int i = 0; i < n; i++) {
                if (buffer[i] == '\n') { if (!oversized && process == child) parseLine(line.toString("UTF-8")); line.reset(); oversized = false; }
                else if (line.size() < 8192) line.write(buffer[i]); else oversized = true;
            }
        } catch (IOException ignored) { /* Process exit is reported by poll, not inferred from a closed stream. */ }
    }
    private void parseLine(String line) {
        Matcher peers = PEERS.matcher(line);
        if (peers.find()) try { synchronized (readiness) { readiness.peers = Integer.parseInt(peers.group(1)); readiness.peersAt = System.currentTimeMillis(); } } catch (NumberFormatException ignored) { }
        if (line.contains("TELENO_ANDROID_NODE_ALREADY_RUNNING")) status("Another node owns the database", "Wait for the previous node to finish shutting down, then retry.");
        if (PRODUCER.equals(mode)) {
            if(productionStatus.accept(line,SystemClock.elapsedRealtime())) { event("Produced a local block at height " + productionStatus.lastHeight() + ". Network finality is separate."); HistoryStore.record(this,network,"Local block produced","Height "+productionStatus.lastHeight()+" · network finality and rewards are checked separately."); }
            synchronized(LOCK){VIEW.production=productionStatus.snapshot();}
        }
        // Only fixed messages reach the screen. Raw engine output may contain secrets or untrusted peer data.
        if (EngineLog.isError(line)) { event("The engine reported an error. Check node status; chain data is preserved."); HistoryStore.record(this,network,"Engine error reported","Check node status. Raw engine output is not saved in history."); }
    }
    private void poll() {
        Process child = process; if (child == null || transitioning) return;
        try {
            if (!child.isAlive()) {
                commands.execute(() -> { if (process == child && !transitioning) finishProcess(child.exitValue()); }); return;
            }
            if (stopRequested) { notifyStatus("Stopping node and flushing database"); return; }
            requireEnvironment(PRODUCER.equals(mode));
            long now = System.currentTimeMillis();
            if (now - wakeAt > 5 * 60 * 1000L) { wake.acquire(10 * 60 * 1000L); wakeAt = now; }
            LocalRpc rpc = new LocalRpc();
            boolean matches;
            JSONObject head;
            try { matches = rpc.chainMatches(network); head = rpc.call("chain.get_head_info", new JSONObject()); }
            catch (Exception e) {
                readFailures++;
                synchronized (LOCK) { VIEW.ready = false; VIEW.readiness = "Local confirmation is unavailable. Production cannot be enabled."; }
                status(now - startedAt < 120000 ? "Starting node" : "Waiting for node response", "The process is running, but its chain status is unavailable. Retrying automatically; no transactions are submitted.");
                if (PRODUCER.equals(mode) && readFailures >= 3) throw new IllegalStateException("Production stopped because this node's chain status could not be verified.");
                return;
            }
            readFailures = 0;
            if (!matches) throw new IllegalStateException("Stopped: the node reported a different chain. Existing data was preserved.");
            String blocker;
            synchronized (readiness) {
                readiness.sample(head.getJSONObject("head_topology").getLong("height"), head.getLong("last_irreversible_block"), head.getLong("head_block_time"), now, true);
                if(readiness.advancedAt==now && readiness.irreversible>0 && readiness.irreversible<=readiness.height) NodeStorage.validated(this,network);
                blocker = readiness.blocker(now);
                synchronized (LOCK) { VIEW.height = readiness.height; VIEW.lib = readiness.irreversible; VIEW.headTime = readiness.headTime; VIEW.peers = readiness.peers;
                    VIEW.production=productionStatus.snapshot();VIEW.sampleAt=now;VIEW.localBlocks=productionStatus.blocks();VIEW.producerRunning=PRODUCER.equals(mode)&&productionStatus.running();VIEW.hasSample = true; VIEW.ready = blocker == null; VIEW.readiness = blocker == null ? (PRODUCER.equals(mode) ? "Local chain is advancing. Use Stop node to stop production." : "Observer is advancing. Producer account and signing key will be checked again before start.") : blocker; }
            }
            if (PRODUCER.equals(mode) && now - startedAt > 90000 && blocker != null) throw new IllegalStateException("Production stopped: " + blocker);
            if (PRODUCER.equals(mode)) {
                ProductionStatus.Snapshot health=productionStatus.snapshot();long elapsed=SystemClock.elapsedRealtime();
                status(health.title(elapsed),
                    health.explanation(elapsed)+"\n"+health.brief(elapsed)
                    + "\nBlocks produced locally this run: " + productionStatus.blocks()
                    + (productionStatus.lastHeight() > 0 ? " · Last height: " + productionStatus.lastHeight() : "")
                    + "\nOpen Production health for diagnostics. Use Stop node to stop production.");
            } else status(blocker == null ? "Observer is up to date" : "Observer is syncing", blocker == null ? "New blocks are being validated on this device." : blocker);
        } catch (Exception e) {
            epoch.incrementAndGet(); stopRequested = true; stopReason = e.getMessage(); child.destroy();
            status("Stopping node", e.getMessage());
            commands.execute(() -> { try { stopNode(false); } catch (Exception ex) { failure(ex); } });
        }
    }
    private void stopNode(boolean force) throws Exception {
        Process child = process;
        if (child == null) { endService(); return; }
        synchronized (LOCK) { VIEW.busy = true; VIEW.ready = false; }
        status(force ? "Forcing shutdown" : "Stopping node", force ? "The next observer start may need database recovery." : (stopReason == null ? "" : stopReason + "\n") + "Flushing chain data. Wait for Stopped before closing the app.");
        if (force) child.destroyForcibly(); else child.destroy();
        if (child.waitFor(30, TimeUnit.SECONDS)) finishProcess(child.exitValue());
        else { synchronized (LOCK) { VIEW.busy = false; } status("Shutdown is taking longer", "The node still owns its database. Keep waiting, or use Force stop if necessary."); }
    }
    private void finishProcess(int exit) {
        synchronized(LOCK){if(VIEW.runStartedAt>0&&VIEW.runEndedAt==0)VIEW.runEndedAt=System.currentTimeMillis();}
        synchronized(LOCK){VIEW.production=productionStatus.snapshot();VIEW.localBlocks=productionStatus.blocks();VIEW.producerRunning=false;}
        process = null; mode = "stopped";
        productionStatus.reset();
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("unfinished-run", false).commit();
        synchronized (readiness) { readiness.reset(); }
        synchronized (LOCK) { VIEW.active = false; VIEW.busy = false; VIEW.mode = "stopped"; VIEW.ready = false; VIEW.readiness = "Run an advancing observer before production."; }
        try { NodeConfig.clearTransientKey(this, network); } catch (Exception e) { event(e.getMessage()); }
        try { NodeConfig.prepare(this, network, null); } catch (Exception e) { event("Observer configuration could not be written. Data was preserved."); }
        status("Stopped", stopRequested ? (stopReason == null ? "Node stopped." : stopReason) + " Chain data is preserved; production will not restart automatically." : "The engine exited (code " + exit + "). Start an observer to recover; no production restart is scheduled.");
        endService();
    }
    private void failure(Throwable e) { String message = e.getMessage() == null ? "Check the device and retry." : e.getMessage(); event("Last action: " + message); status("Action could not complete", message); }
    private void saveDownload(String detail,int percent){getSharedPreferences("settings",MODE_PRIVATE).edit().putString("snapshot-status-"+network,detail).putInt("snapshot-percent-"+network,percent).apply();}
    private void status(String phase, String detail) {
        boolean changed;
        synchronized (LOCK) { changed=!phase.equals(VIEW.phase)||!network.equals(VIEW.network); VIEW.phase = phase; VIEW.detail = detail; VIEW.network = network; }
        if(changed)HistoryStore.record(this,network,phase,"Node status changed.");
        notifyStatus(phase);
    }
    private void event(String value) { synchronized (LOCK) { if (VIEW.events.isEmpty() || !VIEW.events.get(VIEW.events.size() - 1).equals(value)) VIEW.events.add(value); while (VIEW.events.size() > 8) VIEW.events.remove(0); } }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, NodeService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_node).setContentTitle("Teleno · " + network).setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null, "Stop node", stop).build()).build();
    }
    private void notifyStatus(String text) { if (instance == this) getSystemService(NotificationManager.class).notify(NOTICE, notification(text)); }
    private void endService() { if (process != null) return; if (wake.isHeld()) wake.release(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    @Override public void onDestroy() {
        synchronized(LOCK){if(VIEW.runStartedAt>0&&VIEW.runEndedAt==0)VIEW.runEndedAt=System.currentTimeMillis();}
        instance = null; epoch.incrementAndGet(); stopRequested = true;
        Process p = process; if (p != null) p.destroy();
        monitor.shutdownNow(); commands.shutdown(); if (wake != null && wake.isHeld()) wake.release();
        synchronized (LOCK) { VIEW.active = false; VIEW.busy = false; VIEW.ready = false; VIEW.mode = "stopped"; VIEW.phase = "Stopped"; }
        super.onDestroy();
    }
}
