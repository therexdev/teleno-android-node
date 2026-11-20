package com.koinos.teleno;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int INK = UiKit.INK, GREEN = UiKit.TEAL, MUTED = UiKit.MUTED;
    private NodeShell shell;
    private DashboardController dashboard;
    private SilentMode silent;
    private boolean resumed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    // A slow wallet/network call on work must not delay a local production review.
    private final ExecutorService productionReviewWork = Executors.newSingleThreadExecutor();
    private TextView state, detail, metrics, next, engineText, accountText, keyText, recent, productionText;
    private Button start, stop, force, production, networkButton, createKey, importKey, exportKey, accountButton;
    private LinearLayout content;
    private String network;
    private boolean engineAvailable, productionQualified, experimentalProduction, keyBusy, productionReviewLoading;
    private TextView storageText, walletStatus, permissionText;
    private Button checkPermission, reviewPermission, revokePermission, dismissPermission;
    private boolean permissionBusy;
    private TextView burnText,reliabilityText;
    private Button burnButton,checkBurn,dismissBurn;
    private boolean burnBusy;
    private ProductionPermission.Snapshot permissionSnapshot;
    private TextView snapshotText, snapshotSetupText;private Button downloadChain,discardDownload;private ProgressBar snapshotProgress;private boolean snapshotBusy;
    private Button storageButton, cleanStorage, connectWallet, openWallet, disconnectWallet, registerWallet, checkWallet, checkRegistration, dismissRegistration;
    private static volatile VaultClient walletClient;
    private static volatile String walletNotice="";
    private static final AtomicBoolean walletBusy=new AtomicBoolean();
    private long lastWalletPoll;
    private final Runnable tick = new Runnable() { @Override public void run() { refresh(); handler.postDelayed(this, 1500); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Normal screens can be captured for support and sharing. Private-key
        // dialogs set FLAG_SECURE on their own window before showing content.
        network = getSharedPreferences("settings", MODE_PRIVATE).getString("network", "harbinger");
        try {
            JSONObject info = new JSONObject(new String(NodeConfig.asset(this, "engine.json"), StandardCharsets.UTF_8));
            engineAvailable = info.optBoolean("available"); productionQualified = info.optBoolean("productionQualified");
            experimentalProduction = info.optBoolean("experimentalProductionEnabled");
        } catch (Exception ignored) { engineAvailable = productionQualified = experimentalProduction = false; }
        dashboard = new DashboardController(this, () -> { if (!isDestroyed()) refresh(); });
        build(); if(saved!=null) { shell.select(saved.getInt("screen",0),false); shell.setup.post(() -> shell.setup.scrollTo(0,saved.getInt("setup-scroll",0)));if(saved.getBoolean("silent-mode",false))silent.enter(); }
        refresh(); walletAction("",c->{},false,false);
    }
    @Override protected void onResume() { super.onResume(); resumed=true;if(NodeService.resumeRequested(this)&&!NodeService.isActive()) {
        try {startForegroundService(new Intent(this,NodeService.class).setAction(NodeService.RECOVER));}catch(RuntimeException e){NodeService.saveReason(this,"Open Setup and Start observer to restore the saved node run.");}
    }if(silent!=null)silent.foreground(true); if(shell!=null)shell.foreground(silent==null||!silent.active()); handler.removeCallbacks(tick); handler.post(tick); }
    @Override protected void onPause() { resumed=false; handler.removeCallbacks(tick);if(silent!=null)silent.foreground(false); if(shell!=null)shell.foreground(false); super.onPause(); }
    @Override protected void onDestroy() {if(silent!=null)silent.close(); if(dashboard!=null)dashboard.close(); work.shutdown(); productionReviewWork.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if(shell!=null){out.putInt("screen",shell.tab);out.putInt("setup-scroll",shell.setup.getScrollY());}if(silent!=null)out.putBoolean("silent-mode",silent.active()); }
    @Override public void onBackPressed() {if(silent!=null&&silent.active())silent.exit();else if(shell!=null&&shell.tab!=0)shell.select(0,true);else super.onBackPressed(); }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&silent!=null)silent.focus();}
    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return "unknown"; }
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = UiKit.text(this,s,size,color,bold); v.setPadding(0, dp(5), 0, dp(5));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(dp(2), 1); return v;
    }
    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(19), dp(15), dp(19), dp(17));
        box.setBackground(UiKit.surface(this,UiKit.PANEL,22));
        if(title!=null&&title.matches("^[1-5] .*"))box.setTag("setup-"+title.substring(0,1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); content.addView(box, p);
        if (title != null) box.addView(text(title, 18, INK, true)); return box;
    }
    private Button button(LinearLayout box, String title, Runnable action) {
        Button b = UiKit.button(this,title,title.equals("Start observer")||title.startsWith("Review and")||title.equals("Connect KOIN Vault"),action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);box.addView(b,p);return b;
    }
    private void build() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.TRANSPARENT);scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content);
        content.addView(text("TELENO  /  GET STARTED", 12, GREEN, true));
        content.addView(text("Node setup", 30, INK, true));
        content.addView(text("App " + appVersion() + " · " + (ProductionReview.buildAllowsProduction(engineAvailable, productionQualified, experimentalProduction) ? "Block production available" : "Observer mode"), 14, GREEN, true));
        content.addView(text("Follow these steps in order. Your current setup is kept when you update.", 15, MUTED, false));
        LinearLayout status = card(null);
        state = text("Stopped", 25, INK, true); status.addView(state);
        detail = text("", 15, MUTED, false); status.addView(detail);
        metrics = text("", 14, INK, true); status.addView(metrics);
        next = text("", 14, GREEN, false); status.addView(next);
        start = button(status, "Start observer", () -> runNode(NodeService.OBSERVER));
        stop = button(status, "Stop node", () -> send(NodeService.STOP));
        force = button(status, "Force stop…", () -> new AlertDialog.Builder(this).setTitle("Force stop the node?")
            .setMessage("Use this only if shutdown is stuck. The next observer start may need database recovery. Production will remain off.")
            .setNegativeButton("Keep waiting", null).setPositiveButton("Force stop", (d, w) -> send(NodeService.FORCE)).show());
        LinearLayout device = card("1  ·  Check the device and engine");
        engineText = text("", 14, MUTED, false); device.addView(engineText);
        networkButton = button(device, "Network", this::changeNetwork);
        storageText=text("",14,INK,false);device.addView(storageText);
        storageButton=button(device,"Choose phone or SD card storage",this::changeStorage);
        cleanStorage=button(device,"Remove previous database copy…",this::cleanStorage);
        device.addView(text("Chain data can use a removable SD card. Signing keys and wallet connection details stay protected on the phone. Keep the card inserted while the node runs.",14,MUTED,false));
        button(device, "Android notification settings", () -> startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())));
        device.addView(text("Use unmetered Wi-Fi and leave room for the growing chain. The app stops if storage is low or the device overheats. Block production requires power. No Docker, WSL or root is used.", 14, MUTED, false));
        reliabilityText=text("",14,MUTED,false);device.addView(reliabilityText);
        button(device,"Allow unrestricted background running",this::backgroundSettings).setTag("background-running");
        device.addView(text("Automatic recovery keeps a user-started run active through temporary network or process interruptions and reboots. Stop node cancels it. Android force-stop, a powered-off device, missing storage and overheating still interrupt mining.",14,MUTED,false));
        LinearLayout sync = card("2  ·  Download the chain for faster sync");
        sync.addView(text("Optional: download a native Teleno snapshot from the Koinos Foundation to the storage selected above. Review its size, then download, verify and restore it. An observer starts afterward to catch up. No wallet is needed for this step.", 14, MUTED, false));
        snapshotSetupText=text("",14,INK,true);sync.addView(snapshotSetupText);
        snapshotText=text("",14,INK,false);snapshotText.setTextIsSelectable(true);sync.addView(snapshotText);
        snapshotProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);snapshotProgress.setMax(100);sync.addView(snapshotProgress,new LinearLayout.LayoutParams(-1,dp(10)));
        downloadChain=button(sync,"Download chain snapshot…",this::reviewSnapshot);
        discardDownload=button(sync,"Discard unfinished download…",()->new AlertDialog.Builder(this).setTitle("Discard the unfinished download?")
            .setMessage("Only the inactive snapshot download will be deleted. The current chain database, wallet connection and signing keys are kept. You can then check for a newer snapshot or start the observer normally.")
            .setNegativeButton("Keep download",null).setPositiveButton("Discard download",(d,w)->{if(!NodeService.isActive())send(NodeService.DISCARD_DOWNLOAD);}).show());
        sync.addView(text("Progress, speed and errors appear here. Cancel keeps verified files for Resume. Alternatively, tap Start observer above to sync from genesis. Wait for “Observer is up to date” before production setup. Android may stop background work; reopen the app to resume.", 14, MUTED, false));
        LinearLayout wallet = card("3  ·  Connect your wallet");
        wallet.addView(text("Connect KOIN Vault, approve with your passkey, then return here. Your selected wallet becomes the producer account automatically. Vault currently supports this flow on Mainnet. An observer does not need a wallet.", 14, MUTED, false));
        walletStatus=text("",14,MUTED,false);wallet.addView(walletStatus);
        connectWallet=button(wallet,"Connect KOIN Vault",this::connectWallet);
        openWallet=button(wallet,"Create / open KOIN Vault",()->openVaultUrl(VaultClient.HOME));
        checkWallet=button(wallet,"Check wallet connection / approval",()->walletAction("Wallet status",VaultClient::refresh,false,true));
        disconnectWallet=button(wallet,"Disconnect KOIN Vault",()->walletAction("Wallet disconnected",VaultClient::disconnect,false,true));
        accountText = text("", 14, INK, true); accountText.setTextIsSelectable(true); wallet.addView(accountText);
        button(wallet,"Wallet balances / data status",()->{if(shell!=null)shell.showWalletDetails();});
        accountButton = button(wallet, "Use another wallet · enter address manually", this::setAccount);
        wallet.addView(text("Burn KOIN into VHP at this same wallet address. You can also approve the resulting VHP balance for production in the same wallet transaction. Review the exact amounts here and approve in KOIN Vault; burning is irreversible.",14,MUTED,false));
        burnText=text("",14,INK,false);wallet.addView(burnText);
        burnButton=button(wallet,"Burn KOIN → VHP…",this::loadBurn);burnButton.setTag("burn-koin");
        checkBurn=button(wallet,"Check saved KOIN burn",()->walletAction("KOIN burn status",this::checkBurnHistory,false,true));
        dismissBurn=button(wallet,"Resolve expired burn request…",()->new AlertDialog.Builder(this).setTitle("Resolve the saved burn request?")
            .setMessage("First check the exact transaction and pending approvals in KOIN Vault. Closing this saved record does not cancel a burn; a submitted transaction may still complete. Never repeat a burn merely because confirmation is delayed.")
            .setNegativeButton("Keep request",null).setPositiveButton("I checked the wallet",(d,w)->walletAction("Burn record updated",VaultClient::dismissExpiredBurn,false,true)).show());
        LinearLayout key = card("4  ·  Set up a block-signing key");
        key.addView(text("This is a separate key used by the node to sign blocks. Create one here, or import the existing block-signing WIF from your node. Never enter your wallet recovery phrase.", 14, MUTED, false));
        keyText = text("", 14, INK, true); key.addView(keyText); keyText.setTextIsSelectable(true);
        createKey = button(key, "Create block-signing key", this::createKey);
        registerWallet=button(key,"Register signing key with KOIN Vault",this::registerWithWallet);
        checkRegistration=button(key,"Check registration on this node",this::verifyRegistration);
        dismissRegistration=button(key,"Resolve expired registration…",()->new AlertDialog.Builder(this).setTitle("Resolve an expired registration?")
            .setMessage("First check KOIN Vault's history and pending approvals. Dismissing this saved request does not cancel a submitted transaction. Only the public-key registration is involved in this request. Burn and allowance requests are separate explicit reviews. Nothing is resent automatically.")
            .setNegativeButton("Keep saved request",null).setPositiveButton("I checked the wallet",(d,w)->walletAction("Registration record updated",VaultClient::dismissExpiredRegistration,false,true)).show());
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        button(key,"Advanced · import an existing signing key",()->advanced.setVisibility(advanced.getVisibility()==View.GONE?View.VISIBLE:View.GONE));key.addView(advanced);
        importKey = button(advanced, "Import block-signing WIF", this::importKey);
        button(key, "Show / copy public key", this::showPublicKey);
        exportKey = button(key, "Back up signing key privately…", this::backupKey);
        key.addView(text("With KOIN Vault, tap Register signing key, review the public key, then approve in Vault and return here. For another wallet, use the public key above with PoB register_public_key. The private block-signing key stays on this device. Do not run the same producer key on two nodes.", 14, MUTED, false));
        LinearLayout produce = card("5  ·  Check and start production");
        produce.addView(text("The app will recheck the local chain, recent block progress, peers, VHP and the registered public key. It then stops the observer cleanly and restarts with production enabled. Every restart requires a fresh observer check.", 14, MUTED, false));
        permissionText=text("",14,INK,false);produce.addView(permissionText);
        produce.addView(text("KOIN Vault smart accounts also need a VHP allowance for the official PoB contract. Registering the signing key alone does not grant this permission. Choose a limited amount; successful blocks consume it for rewards. No tokens move when setting the allowance.",14,MUTED,false));
        checkPermission=button(produce,"Check VHP permission",()->loadPermission(false,false));checkPermission.setTag("check-vhp-permission");
        reviewPermission=button(produce,"Review VHP production permission…",()->loadPermission(true,false));reviewPermission.setTag("review-vhp-permission");
        revokePermission=button(produce,"Revoke VHP permission…",()->loadPermission(true,true));
        dismissPermission=button(produce,"Resolve expired permission…",()->new AlertDialog.Builder(this).setTitle("Resolve an expired permission request?")
            .setMessage("Check KOIN Vault's transaction history and pending approvals first. Closing this saved record does not cancel a submitted transaction or revoke an allowance. The transaction might still complete. Nothing will be resent. Check the local allowance before making a new request.")
            .setNegativeButton("Keep request",null).setPositiveButton("I checked the wallet",(d,w)->walletAction("Permission record updated",VaultClient::dismissExpiredAllowance,false,true)).show());
        productionText = text("", 14, INK, true); produce.addView(productionText);
        production = button(produce, "Review and start block production…", this::reviewProduction);
        button(produce,"Production health / copy report",()->{if(shell!=null)shell.showProductionHealth();}).setTag("production-health");
        if (!productionQualified) produce.addView(text(experimentalProduction
            ? "Keep the phone powered and cool; use Stop node to stop production. Automatic recovery resumes this reviewed producer after fresh observer checks. Stop node cancels recovery."
            : "This app is observer-only. Install the full Teleno Node app to enable block production.", 14, UiKit.AMBER, true));
        LinearLayout activity = card("What's happening"); recent = text("", 14, MUTED, false); activity.addView(recent);
        button(activity, "Open-source notices", this::showNotices);
        content.addView(text("App " + appVersion() + " · Teleno engine 1.3.0\nUninstalling or clearing app data removes the node database and device-bound signing key. Save your key backup first.", 12, MUTED, false));
        shell=new NodeShell(this,scroll,()->{NodeService.ViewState current=NodeService.snapshot();if(current.active||current.busy)stop.performClick();else start.performClick();},()->dashboard.refresh(),()->silent.enter());
        FrameLayout stage=new FrameLayout(this);stage.addView(shell.root,new FrameLayout.LayoutParams(-1,-1));
        silent=new SilentMode(this,stage,shell.root,()->{if(!silent.active())shell.select(0,false);shell.foreground(resumed&&!silent.active());});
        setContentView(stage);

    }
    private void refresh() {
        NodeService.ViewState v = NodeService.snapshot();
        boolean arm = Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a");
        long free=-1;String storageProblem="";
        try {free=NodeStorage.free(this,network);}catch(Exception e){storageProblem=e.getMessage();}
        String freeLabel=free<0?"Storage unavailable":String.format(java.util.Locale.ROOT,"%.1f GiB free",free/1073741824.0);
        storageText.setText("Chain storage: "+NodeStorage.label(this,network)+"\n"+freeLabel+(storageProblem.isEmpty()?"":"\n"+storageProblem)
            +(NodeStorage.retained(this,network)?"\nPrevious database copy retained. After the new observer advances, stop it and remove that copy to reclaim space.":""));
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo(); getSystemService(ActivityManager.class).getMemoryInfo(memory);
        state.setText(v.phase); detail.setText(!v.active && !v.busy && getSharedPreferences("settings", MODE_PRIVATE).getBoolean("unfinished-run", false) ? "The previous run was interrupted. Chain data was preserved. Start an observer to recover; automatic recovery will retry the saved run." : v.detail);
        android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
        boolean unrestricted=power.isIgnoringBatteryOptimizations(getPackageName());
        String reason=getSharedPreferences("settings",MODE_PRIVATE).getString("last-run-reason","");
        long reasonAt=getSharedPreferences("settings",MODE_PRIVATE).getLong("last-run-reason-at",0);
        reliabilityText.setText("Battery optimization: "+(unrestricted?"unrestricted":"restricted — allow background running below")
            +"\nAutomatic recovery: "+(NodeService.resumeRequested(this)?"on · Stop node cancels it":"off · enabled when you start the node")
            +(reason.isEmpty()?"":"\nLast interruption / stop: "+reason+"\n"+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(reasonAt))));
        boolean selectedSample = v.hasSample && network.equals(v.network);
        metrics.setText((selectedSample ? (v.active ? "Block " : "Last observed block ") + String.format(java.util.Locale.ROOT, "%,d", v.height)
            + "  ·  Irreversible " + String.format(java.util.Locale.ROOT, "%,d", v.lib) : "Block waiting  ·  Irreversible waiting")
            + "\nPeers " + (!network.equals(v.network) || v.peers < 0 ? "waiting" : v.peers) + "  ·  " + freeLabel);
        next.setText(v.active ? v.readiness : "Next: " + (engineAvailable ? "choose chain storage in step 1, then download a snapshot in step 2. Or use Start observer to sync from genesis on " + label() + "." : "a complete Android engine build is required. This APK cannot run a node yet."));
        engineText.setText(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + "\n"
            + String.format(java.util.Locale.ROOT, "%.1f GiB RAM · ", memory.totalMem / 1073741824.0) + freeLabel
            + "\n" + (arm ? "ARM64 is supported by this app." : "This app requires a 64-bit ARM Android installation.")
            + "\n" + (engineAvailable ? "Node engine installed." : "Node engine missing. Install the full Teleno Node app."));
        VaultClient.View wallet=walletClient==null?null:walletClient.view();boolean pending=wallet!=null&&wallet.pending;
        boolean savedDownload=SnapshotBootstrap.pending(this,network);
        if(!v.active&&savedDownload)next.setText("Next: resume the saved chain download in step 2, or discard it to use normal observer sync.");
        if(v.busy&&!v.active)next.setText("The operation is running. Progress and the next step appear above.");
        networkButton.setText("Network: " + label() + " · Change"); networkButton.setEnabled(!v.active && !v.busy && !keyBusy && !walletBusy.get() && !pending&&!snapshotBusy);
        start.setEnabled(engineAvailable && arm && free>=0 && !v.active && !v.busy && !keyBusy&&!savedDownload&&!snapshotBusy);
        storageButton.setEnabled(!v.active&&!v.busy&&!keyBusy&&!snapshotBusy&&!savedDownload);
        SnapshotSetup setup=snapshotSetup(v,free,storageProblem);
        downloadChain.setText(setup.label);downloadChain.setEnabled(setup.enabled());
        snapshotSetupText.setText("Network: "+label()+"\n"+setup.note);
        discardDownload.setVisibility(savedDownload?View.VISIBLE:View.GONE);discardDownload.setEnabled(!v.active&&!v.busy&&!snapshotBusy);
        boolean liveDownload=v.downloading&&network.equals(v.network);
        String downloadNote=getSharedPreferences("settings",MODE_PRIVATE).getString("snapshot-status-"+network,"");
        snapshotText.setText(liveDownload?v.phase+"\n"+v.detail:downloadNote);
        snapshotText.setVisibility(snapshotBusy||(!liveDownload&&downloadNote.isEmpty())?View.GONE:View.VISIBLE);
        int percent=liveDownload?v.downloadPercent:getSharedPreferences("settings",MODE_PRIVATE).getInt("snapshot-percent-"+network,-1);
        snapshotProgress.setVisibility(snapshotBusy||liveDownload||savedDownload?View.VISIBLE:View.GONE);snapshotProgress.setIndeterminate(snapshotBusy||percent<0);if(percent>=0)snapshotProgress.setProgress(percent);
        cleanStorage.setVisibility(NodeStorage.retained(this,network)?View.VISIBLE:View.GONE);cleanStorage.setEnabled(!v.active&&!v.busy&&NodeStorage.canClean(this,network));
        stop.setText(v.busy&&!v.active?"Cancel operation":"Stop node");stop.setEnabled((v.active || v.busy)&&!"Removing previous copy".equals(v.phase)&&!"Removing unfinished download".equals(v.phase)); force.setVisibility(v.active && (v.phase.contains("Shutdown") || v.phase.contains("Stopping")) ? View.VISIBLE : View.GONE);
        String account = getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + network, ""); accountText.setText(account.isEmpty() ? "No producer account selected" : account);
        boolean exists = new KeyVault(this, network).exists(); keyText.setText(exists ? "Signing key saved on this device" : "No signing key yet");
        createKey.setEnabled(engineAvailable && !exists && !keyBusy && !walletBusy.get() && !pending && !v.busy && !NodeService.PRODUCER.equals(v.mode));
        importKey.setEnabled(createKey.isEnabled()); exportKey.setEnabled(exists && !keyBusy && !v.busy && !NodeService.PRODUCER.equals(v.mode));
        accountButton.setEnabled(!v.busy && !walletBusy.get() && !pending && (wallet==null||!wallet.connected) && !NodeService.PRODUCER.equals(v.mode));
        boolean walletAllowed=!walletBusy.get()&&!v.busy&&!NodeService.PRODUCER.equals(v.mode)&&!keyBusy;
        connectWallet.setEnabled(walletAllowed&&!pending);connectWallet.setText(wallet!=null&&wallet.connected?"KOIN Vault connected · reopen":"Connect KOIN Vault");
        openWallet.setEnabled(!walletBusy.get());checkWallet.setEnabled(!walletBusy.get());disconnectWallet.setEnabled(walletAllowed&&wallet!=null&&(wallet.connected||wallet.pairing));
        registerWallet.setEnabled(walletAllowed&&"mainnet".equals(network)&&exists&&wallet!=null&&wallet.connected&&!pending);
        checkRegistration.setEnabled(walletAllowed&&exists&&v.ready&&"mainnet".equals(network));
        dismissRegistration.setVisibility(pending&&VaultClient.REGISTRATION.equals(wallet.kind)?View.VISIBLE:View.GONE);dismissRegistration.setEnabled(walletAllowed);
        walletStatus.setText((wallet==null?"Wallet not connected.":wallet.note+(wallet.txid.isEmpty()?"":"\nTransaction: "+wallet.txid))
            +(walletNotice.isEmpty()?"":"\n"+walletNotice)+(walletBusy.get()?"\nChecking KOIN Vault…":""));
        // Review is informational until the separate confirmation action. Never
        // disable it for sync, missing setup or asynchronous wallet polling.
        production.setEnabled(true);
        boolean pendingBurn=pending&&VaultClient.BURN.equals(wallet.kind);
        burnButton.setEnabled(!burnBusy&&!permissionBusy&&!pending);
        checkBurn.setVisibility(wallet!=null&&VaultClient.BURN.equals(wallet.kind)?View.VISIBLE:View.GONE);
        dismissBurn.setVisibility(pendingBurn?View.VISIBLE:View.GONE);
        burnText.setText(burnBusy?"Checking the exact KOIN, VHP, mana and PoB allowance…":pendingBurn?"Saved burn: "+WalletReader.amount(wallet.amount)+" KOIN\n"+wallet.note+(wallet.txid.isEmpty()?"":"\nTransaction: "+wallet.txid):"Burns and VHP approvals require your separate wallet approval. They are never repeated automatically.");
        boolean pendingPermission=pending&&VaultClient.ALLOWANCE.equals(wallet.kind);
        checkPermission.setEnabled(!permissionBusy);
        reviewPermission.setEnabled(!permissionBusy);revokePermission.setEnabled(!permissionBusy);
        dismissPermission.setVisibility(pendingPermission?View.VISIBLE:View.GONE);dismissPermission.setEnabled(walletAllowed);
        String permissionNote=permissionSnapshot!=null&&permissionSnapshot.account.equals(account)&&"mainnet".equals(network)
            ?permissionSnapshot.status():"VHP production permission has not been checked for this account. Start an up-to-date Mainnet observer, then tap Check VHP permission.";
        if(pendingPermission)permissionNote+="\n\nSaved permission request: "+WalletReader.amount(wallet.amount)+" VHP. "+wallet.note+(wallet.txid.isEmpty()?"":"\nTransaction: "+wallet.txid);
        permissionText.setText(permissionBusy?"Checking the local VHP balance and PoB allowance…":permissionNote);
        productionText.setText(productionSetup(v).note + productionProblem(v));
        recent.setText(v.events.isEmpty() ? "No node events yet. Progress and next steps appear above." : android.text.TextUtils.join("\n\n", v.events));
        dashboard.select(network,account);dashboard.run(v);if(resumed)dashboard.tick();
        shell.bind(v,dashboard,start.isEnabled(),stop.isEnabled(),network,account,exists,free,appVersion());
        silent.view.bind(v,dashboard);
        if(wallet!=null&&(wallet.pairing||wallet.connected)&&System.currentTimeMillis()-lastWalletPoll>6000&&!walletBusy.get()) {
            lastWalletPoll=System.currentTimeMillis();walletAction("",VaultClient::refresh,false,false);
        }
    }
    private String label() { return "mainnet".equals(network) ? "Mainnet" : "Harbinger testnet"; }
    private void changeNetwork() {
        if (NodeService.isActive() || keyBusy || walletBusy.get() || (walletClient!=null&&walletClient.view().pending)) return;
        new AlertDialog.Builder(this).setTitle("Choose network").setItems(new String[]{"Harbinger testnet", "Mainnet"}, (d, which) -> {
            if(NodeService.isActive()||walletBusy.get()||(walletClient!=null&&walletClient.view().pending))return;
            network = which == 0 ? "harbinger" : "mainnet";
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("network", network).apply(); HistoryStore.record(this,network,"Network selected",label());refresh();
        }).show();
    }
    private void send(String action) { HistoryStore.record(this,network,"Action requested: "+action,""); startForegroundService(new Intent(this, NodeService.class).setAction(action).putExtra("network", network)); }
    private SnapshotSetup snapshotSetup(NodeService.ViewState v,long free,String storageProblem) {
        SnapshotSetup.State s=new SnapshotSetup.State();
        s.engine=engineAvailable;s.arm64=Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a");
        s.active=v.active;s.busy=v.busy;s.phase=v.phase;s.downloading=v.downloading;
        s.checking=snapshotBusy;s.keyBusy=keyBusy;s.free=free;s.storageProblem=storageProblem==null?"":storageProblem;
        s.saved=SnapshotBootstrap.pending(this,network);s.retained=NodeStorage.retained(this,network);s.validated=NodeStorage.canClean(this,network);
        return SnapshotSetup.from(s);
    }
    private void reviewSnapshot() {
        long availableBytes=-1;String storageProblem="";
        try{availableBytes=NodeStorage.free(this,network);}catch(Exception e){storageProblem=e.getMessage();}
        SnapshotSetup setup=snapshotSetup(NodeService.snapshot(),availableBytes,storageProblem);
        if(setup.action==SnapshotSetup.Action.STOP_NODE) {
            new AlertDialog.Builder(this).setTitle("Stop the node for snapshot setup?").setMessage(setup.note+"\n\nStopping preserves the current database. You will review the snapshot and its size before any download starts.")
                .setNegativeButton("Keep running",null).setPositiveButton("Stop node",(d,w)->send(NodeService.STOP)).show();return;
        }
        if(setup.action==SnapshotSetup.Action.CLEAN_PREVIOUS){cleanStorage();return;}
        if(setup.action==SnapshotSetup.Action.START_OBSERVER) {
            new AlertDialog.Builder(this).setTitle("Validate chain storage first").setMessage(setup.note)
                .setNegativeButton("Not now",null).setPositiveButton("Start observer",(d,w)->runNode(NodeService.OBSERVER)).show();return;
        }
        if(setup.action!=SnapshotSetup.Action.REVIEW){message("Snapshot setup",setup.note);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);return;}
        final String chosen=network;final String selected=NodeStorage.volume(this,chosen);snapshotBusy=true;refresh();
        work.execute(()->{
            try {
                SnapshotPlan plan=SnapshotBootstrap.review(this,chosen);long free=NodeStorage.free(this,chosen);boolean resume=SnapshotBootstrap.pending(this,chosen);
                handler.post(()->{
                    snapshotBusy=false;if(isFinishing()||isDestroyed())return;refresh();
                    if(NodeService.isActive()||!chosen.equals(network)||!selected.equals(NodeStorage.volume(this,chosen)))return;
                    String info="Network: "+label()+"\nStorage: "+NodeStorage.label(this,chosen)+"\nSnapshot: "+plan.created
                        +(plan.head>0?"\nStarting block: "+String.format(java.util.Locale.ROOT,"%,d",plan.head):"")
                        +"\nChain files: "+SnapshotDownload.gib(plan.bytes)+" GiB\nFree space: "+SnapshotDownload.gib(free)+" GiB\n"
                        +(resume?"Verified files are reused. Remaining space is checked before downloading more.":"Requires "+SnapshotDownload.gib(plan.bytes+SnapshotPlan.RESERVE)+" GiB free, including a 2 GiB reserve. Existing chain data stays in place.")
                        +"\n\nThis uses the Foundation's published chain state, then validates new blocks locally. Every downloaded file is checked against its published SHA-256 checksum. Your existing chain is kept until the restored observer advances. Wallet keys and configuration are preserved. Production stays off.";
                    AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(resume?"Resume this chain download?":"Download and sync the chain?").setMessage(info).setNegativeButton("Not now",null);
                    if(resume||free>=plan.bytes+SnapshotPlan.RESERVE)dialog.setPositiveButton(resume?"Resume":"Download and sync",(d,w)->{
                        if(NodeService.isActive()||!chosen.equals(network)||!selected.equals(NodeStorage.volume(this,chosen)))return;
                        startForegroundService(new Intent(this,NodeService.class).setAction(NodeService.DOWNLOAD_CHAIN).putExtra("network",chosen).putExtra("snapshot_id",plan.id).putExtra("manifest_hash",plan.manifestHash).putExtra("files_hash",plan.filesHash));
                    });
                    else dialog.setNeutralButton("Free up storage first",(d,w)->{});
                    dialog.show();
                });
            }catch(Exception e){handler.post(()->{snapshotBusy=false;if(!isFinishing()&&!isDestroyed()){getSharedPreferences("settings",MODE_PRIVATE).edit().putString("snapshot-status-"+chosen,"Snapshot check failed: "+e.getMessage()+"\nExisting chain data was preserved. Follow the message above before retrying.").apply();refresh();}});}
        });
    }
    private void changeStorage() {
        if(NodeService.isActive())return;
        try {
        java.util.List<NodeStorage.Choice> choices=NodeStorage.choices(this);
        String[] labels=new String[choices.size()];for(int i=0;i<labels.length;i++){NodeStorage.Choice c=choices.get(i);labels[i]=c.label+String.format(java.util.Locale.ROOT," · %.1f GiB free",c.free()/1073741824.0);}
        new AlertDialog.Builder(this).setTitle("Chain storage · "+label()).setMessage(choices.size()==1?"No writable removable SD card is detected. Insert a card and mount it in Android's storage settings. The phone's emulated shared storage is not a separate SD card.":null)
            .setItems(labels,(d,index)->{
                NodeStorage.Choice choice=choices.get(index);
                new AlertDialog.Builder(this).setTitle("Use "+choice.label+"?")
                    .setMessage("The node must be stopped. Existing chain data will be copied and checksum-verified before switching. Progress appears on the front page. The old copy is kept until the new observer advances; you can then remove it. Signing keys stay on the phone. Android removes app-specific card data when the app is uninstalled.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Copy and switch",(dialog,w)->{
                        if(NodeService.isActive())return;
                        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);return;}
                        startForegroundService(new Intent(this,NodeService.class).setAction(NodeService.MOVE_STORAGE).putExtra("network",network).putExtra("volume",choice.id));
                    }).show();
            }).setNegativeButton("Done",null).show();
        }catch(Exception e){message("Storage unavailable","Android could not inspect the card. Reinsert it and try again. Existing chain data was preserved.");}
    }
    private void cleanStorage(){new AlertDialog.Builder(this).setTitle("Remove the previous database copy?")
        .setMessage("The observer has advanced on the selected storage. This permanently removes only the retained old database to free space. The current database, wallet connection and signing keys are kept.")
        .setNegativeButton("Keep copy",null).setPositiveButton("Remove previous copy",(d,w)->{if(!NodeService.isActive())send(NodeService.CLEAN_STORAGE);}).show();}
    private void runNode(String action) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10); return;
        }
        send(action);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if(request==11) {
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)reviewSnapshot();
            else {
                getSharedPreferences("settings",MODE_PRIVATE).edit().putString("snapshot-status-"+network,"Notifications are required for download progress and the stop control. Enable Teleno notifications in Android settings, then tap Download chain snapshot again.").apply();refresh();
                new AlertDialog.Builder(this).setTitle("Enable download notifications").setMessage("Allow Teleno notifications so download progress and the stop control remain visible. Return here afterward and tap Download chain snapshot.")
                    .setNegativeButton("Not now",null).setPositiveButton("Open settings",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))).show();
            }
        }
        else if (request == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) message("Notifications enabled", "Tap Start observer to begin. The notification includes Stop node.");
        else if (request == 10) message("Notification access needed", "Enable Teleno notifications in Android settings so the running node and stop control remain visible.");
    }
    private void setAccount() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Koinos account address"); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(this).setTitle("Producer account · " + label()).setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save address", (d, which) -> { try {
                if(walletBusy.get()||(walletClient!=null&&(walletClient.view().pending||walletClient.view().connected))||NodeService.PRODUCER.equals(NodeService.snapshot().mode)) throw new IllegalStateException("Disconnect KOIN Vault and finish its pending registration before changing accounts.");
                String account = input.getText().toString().trim(); Base58.address(account);
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("producer-" + network, account).apply(); HistoryStore.record(this,network,"Producer account selected",account);refresh();
            } catch (Exception e) { message("Address not saved", e.getMessage()); } }).show();
    }
    private interface WalletTask { void run(VaultClient client) throws Exception; }
    private void walletAction(String title,WalletTask task,boolean open,boolean notify) {
        if(!walletBusy.compareAndSet(false,true)){if(notify)message("Wallet check in progress","Wait for the current wallet check, then tap again. No new request was sent.");return;}
        lastWalletPoll=System.currentTimeMillis();
        work.execute(()->{
            String error=null,url=null;
            try {
                if(walletClient==null)walletClient=new VaultClient(new VaultTransport(),new VaultStore(getApplicationContext()),System::currentTimeMillis);
                task.run(walletClient);
                if(open)url=walletClient.openUrl();
                VaultClient.View v=walletClient.view();
                if(!v.knownAddress.isEmpty())getSharedPreferences("settings",MODE_PRIVATE).edit().putString("vault-producer-mainnet",v.knownAddress).apply();
                NodeService.ViewState node = NodeService.snapshot();
                if(v.connected&&!node.busy&&!NodeService.PRODUCER.equals(node.mode)&&!NodeService.resumeRequested(this)) getSharedPreferences("settings",MODE_PRIVATE).edit().putString("producer-mainnet",v.address).apply();
                walletNotice="";
                if(notify&&!title.isEmpty())HistoryStore.record(this,"mainnet",title,"KOIN Vault action completed; on-chain confirmation is separate.");
            }catch(Exception e){error=e.getMessage()==null?"Wallet connection unavailable. Saved requests were preserved.":e.getMessage();walletNotice=error;}
            finally{walletBusy.set(false);}
            final String problem=error, destination=url;
            handler.post(()->{
                if(isDestroyed())return;
                refresh();
                if(problem!=null&&notify)message("KOIN Vault needs attention",problem);
                else if(destination!=null)openVaultUrl(destination);
                else if(problem==null&&notify)message(title,walletClient.view().note);
            });
        });
    }
    private void openVaultUrl(String url) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
        catch(ActivityNotFoundException e){message("Browser needed","Install or enable a browser that supports passkeys, then open KOIN Vault again.");}
    }
    private void connectWallet() {
        if(!"mainnet".equals(network)){
            if(NodeService.isActive()){message("Mainnet connection","KOIN Vault's producer flow supports Mainnet. Stop the observer before switching networks. You can continue using Harbinger without a wallet.");return;}
            new AlertDialog.Builder(this).setTitle("Connect KOIN Vault on Mainnet?")
                .setMessage("This changes the selected node network to Mainnet. Harbinger data is kept separately. Connecting only shares the account address after your passkey approval; it does not start production or spend tokens.")
                .setNegativeButton("Keep Harbinger",null).setPositiveButton("Switch and connect",(d,w)->{
                    if(NodeService.isActive()||walletBusy.get())return;network="mainnet";getSharedPreferences("settings",MODE_PRIVATE).edit().putString("network",network).apply();
                    walletAction("Connect KOIN Vault",VaultClient::connect,true,true);
                }).show();
        }else walletAction("Connect KOIN Vault",VaultClient::connect,true,true);
    }
    private void registerWithWallet(){
        if(walletClient==null||walletBusy.get()||keyBusy||!"mainnet".equals(network))return;
        final String account=walletClient.view().address;if(account.isEmpty())return;keyBusy=true;refresh();
        work.execute(()->{
            try{
                byte[] publicKey=new KeyVault(this,"mainnet").publicKey();String encoded=LocalRpc.b64(publicKey);
                handler.post(()->{
                    keyBusy=false;if(isDestroyed())return;refresh();
                    new AlertDialog.Builder(this).setTitle("Register this key with KOIN Vault?")
                        .setMessage("Network: Mainnet\nProducer: "+account+"\nPublic signing key: "+encoded+"\n\nThis lets the node sign blocks for this account. No tokens move. Back up the separate signing key first and stop any other node using it. Registration does not start production; review step 5 after the observer catches up. Vault will ask for your passkey before submitting.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Review in KOIN Vault",(d,w)->walletAction("Registration requested",c->{
                            if(!"mainnet".equals(getSharedPreferences("settings",MODE_PRIVATE).getString("network",""))||NodeService.PRODUCER.equals(NodeService.snapshot().mode))throw new IllegalStateException("The node context changed. Review the registration again.");
                            if(!java.security.MessageDigest.isEqual(publicKey,new KeyVault(this,"mainnet").publicKey()))throw new IllegalStateException("The signing key changed. Review again.");
                            c.register(account,publicKey);
                        },true,true)).show();
                });
            }catch(Exception e){handler.post(()->{keyBusy=false;if(!isDestroyed()){refresh();message("Signing key unavailable",e.getMessage());}});}
        });
    }
    private void verifyRegistration(){
        if(walletClient!=null&&walletClient.view().pending&&VaultClient.ALLOWANCE.equals(walletClient.view().kind)){message("Permission request pending","Use Check VHP permission in step 5 to check the saved allowance. Your signing-key registration is separate.");return;}
        walletAction("Local registration check",c->{
        NodeService.ViewState v=NodeService.snapshot();if(!"mainnet".equals(v.network)||!v.ready||!v.active)throw new IllegalStateException("Run an advancing Mainnet observer before checking the registered key.");
        String account=getSharedPreferences("settings",MODE_PRIVATE).getString("producer-mainnet","");Base58.address(account);
        byte[] key=new KeyVault(this,"mainnet").publicKey();LocalRpc rpc=new LocalRpc();
        if(!rpc.chainMatches("mainnet"))throw new IllegalStateException("The local node is on another network.");
        byte[] registered=Proto.valueBytes(rpc.readContract("159myq5YUhhoVWu3wsHKHiJYKPKGUrGiyv",2523090792L,Proto.bytes(Base58.address(account))));
        if(!java.security.MessageDigest.isEqual(key,registered))throw new IllegalStateException("This public key is not visible in the local node yet. Check the wallet approval and allow the observer to catch up.");
        c.observed(account,key);
    },false,true);}
    private String permissionAccount(boolean editing) throws Exception {
        if(!"mainnet".equals(network)||!"mainnet".equals(getSharedPreferences("settings",MODE_PRIVATE).getString("network","")))throw new IllegalStateException("VHP permission through KOIN Vault is available on Mainnet. Select Mainnet in step 1.");
        NodeService.ViewState v=NodeService.snapshot();
        if(editing&&NodeService.PRODUCER.equals(v.mode))throw new IllegalStateException("Stop production, then Start observer. Wait for it to be up to date before changing VHP permission.");
        if(v.busy||!v.active||!v.ready||!"mainnet".equals(v.network))throw new IllegalStateException("Start a Mainnet observer and wait until it is up to date, then check permission again.");
        String account=getSharedPreferences("settings",MODE_PRIVATE).getString("producer-mainnet","");
        if(account.isEmpty())throw new IllegalStateException("Select your producer wallet in step 3 first.");Base58.address(account);return account;
    }
    private void loadPermission(boolean edit,boolean revoke){
        if(permissionBusy)return;
        final String account;
        try{
            account=permissionAccount(edit);
            if(edit&&(walletClient==null||!walletClient.view().connected||!account.equals(walletClient.view().address)))throw new IllegalStateException("Connect this producer account to KOIN Vault in step 3 first. Checking an existing allowance does not require a live wallet connection.");
            if(edit&&walletClient.view().pending)throw new IllegalStateException("A wallet request is already saved. Check its approval and local state before requesting another change.");
        }catch(Exception e){message("VHP permission setup",e.getMessage());return;}
        permissionBusy=true;refresh();
        productionReviewWork.execute(()->{
            try{
                LocalRpc rpc=new LocalRpc();ProductionPermission.Snapshot snapshot=ProductionPermission.read(rpc::call,account,System.currentTimeMillis());
                if(!account.equals(permissionAccount(edit)))throw new IllegalStateException("The selected account changed. Check permission again.");
                handler.post(()->{permissionBusy=false;if(isDestroyed())return;permissionSnapshot=snapshot;refresh();
                    if(edit)reviewPermissionAmount(snapshot,revoke);
                    else {
                        message("VHP production permission",snapshot.status());
                        // Queue behind a wallet poll without delaying the read-only result.
                        work.execute(()->{try{VaultClient c=walletClient;if(c!=null&&c.view().pending&&VaultClient.ALLOWANCE.equals(c.view().kind)
                            &&account.equals(permissionAccount(false))&&snapshot.allowance.toString().equals(c.view().amount))c.observedAllowance(account,snapshot.allowance);
                            handler.post(()->{if(!isDestroyed())refresh();});}catch(Exception ignored){/* The durable request remains unresolved. */}});
                    }
                });
            }catch(Exception e){handler.post(()->{permissionBusy=false;if(!isDestroyed()){refresh();message("Permission check unavailable",e.getMessage());}});}
        });
    }
    private void reviewPermissionAmount(ProductionPermission.Snapshot snapshot,boolean revoke){
        if(revoke){showPermissionReview(snapshot,java.math.BigInteger.ZERO);return;}
        EditText amount=new EditText(this);amount.setSingleLine(true);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(WalletReader.amount(snapshot.balance.min(new java.math.BigInteger("100000000000")).toString()));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose a limited VHP allowance")
            .setMessage("Amount in VHP for official PoB block production. This replaces the remaining allowance. Start with a limited allowance and renew when needed.\n\nWallet balance: "+WalletReader.amount(snapshot.balance.toString())+" VHP\nRemaining allowance: "+WalletReader.amount(snapshot.allowance.toString())+" VHP")
            .setView(amount).setNegativeButton("Cancel",null).setPositiveButton("Review exact permission",null).create();
        dialog.setOnShowListener(d->dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{try{
            java.math.BigInteger value=ProductionPermission.amount(amount.getText().toString().trim());
            if(value.signum()==0)throw new IllegalArgumentException("Enter a positive amount, or use Revoke VHP permission to set zero.");
            ProductionPermission.requireCurrent(snapshot,snapshot,value,System.currentTimeMillis());dialog.dismiss();showPermissionReview(snapshot,value);
        }catch(Exception e){amount.setError(e.getMessage());}}));dialog.show();
    }
    // Package-visible so UI tests can inspect the exact review without a wallet or node.
    void showPermissionReview(ProductionPermission.Snapshot snapshot,java.math.BigInteger amount){
        new AlertDialog.Builder(this).setTitle(amount.signum()==0?"Revoke VHP production permission?":"Review VHP production permission")
            .setMessage(ProductionPermission.review(snapshot,amount)).setNegativeButton("Cancel",null)
            .setPositiveButton("Review in KOIN Vault",(d,w)->walletAction("VHP permission requested",c->c.approveProductionAllowance(snapshot.account,amount,()->{
                String account=permissionAccount(true);LocalRpc rpc=new LocalRpc();
                ProductionPermission.Snapshot current=ProductionPermission.read(rpc::call,account,System.currentTimeMillis());
                if(!account.equals(permissionAccount(true)))throw new IllegalStateException("The node context changed. Review permission again.");
                ProductionPermission.requireCurrent(snapshot,current,amount,System.currentTimeMillis());
            }),true,true)).show();
    }
    private void checkBurnHistory(VaultClient client)throws Exception {
        Exception last=null;
        for(String endpoint:PublicRpc.endpoints("mainnet"))try {client.checkBurn(new PublicRpc(endpoint));return;}
        catch(Exception e){last=e;}
        throw last==null?new IllegalStateException("The burn confirmation could not be checked."):last;
    }
    private void backgroundSettings() {
        try {
            android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
            if(!power.isIgnoringBatteryOptimizations(getPackageName()))startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));
            else startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        }catch(ActivityNotFoundException e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}
    }
    private void loadBurn() {
        if(burnBusy)return;final String account;
        try {
            account=permissionAccount(true);
            if(walletClient==null||!walletClient.view().connected||!account.equals(walletClient.view().address))throw new IllegalStateException("Connect the producer account to KOIN Vault in step 3 first.");
            if(walletClient.view().pending)throw new IllegalStateException("Resolve the saved wallet request before preparing a burn.");
        }catch(Exception e){message("KOIN burn setup",e.getMessage());return;}
        burnBusy=true;refresh();
        productionReviewWork.execute(()->{try{
            LocalRpc rpc=new LocalRpc();BurnKoin.Snapshot snapshot=BurnKoin.read(rpc::call,account,System.currentTimeMillis());
            if(!account.equals(permissionAccount(true)))throw new IllegalStateException("The selected account changed. Review the burn again.");
            handler.post(()->{burnBusy=false;if(!isDestroyed()){refresh();chooseBurn(snapshot);}});
        }catch(Exception e){handler.post(()->{burnBusy=false;if(!isDestroyed()){refresh();message("Burn check unavailable",e.getMessage());}});}});
    }
    private void chooseBurn(BurnKoin.Snapshot snapshot) {
        LinearLayout fields=new LinearLayout(this);fields.setPadding(dp(24),0,dp(24),0);fields.setOrientation(LinearLayout.VERTICAL);
        EditText amount=new EditText(this);amount.setSingleLine(true);amount.setHint("KOIN amount");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);fields.addView(amount);
        CheckBox approve=new CheckBox(this);approve.setText("Also approve the resulting VHP balance for production");approve.setChecked(true);fields.addView(approve);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Burn KOIN into VHP")
            .setMessage("KOIN balance: "+WalletReader.amount(snapshot.koin.toString())+"\nVHP balance: "+WalletReader.amount(snapshot.permission.balance.toString())
                +"\nMaximum burn now: "+WalletReader.amount(snapshot.maximum().toString())+" KOIN (mana reserve included). Burning cannot be undone.")
            .setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Review exact transaction",null).create();
        dialog.setOnShowListener(d->dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{try{
            java.math.BigInteger value=BurnKoin.amount(amount.getText().toString().trim());
            BurnKoin.requireCurrent(snapshot,snapshot,value,approve.isChecked(),System.currentTimeMillis());dialog.dismiss();showBurnReview(snapshot,value,approve.isChecked());
        }catch(Exception e){amount.setError(e.getMessage());}}));dialog.show();
    }
    void showBurnReview(BurnKoin.Snapshot snapshot,java.math.BigInteger amount,boolean approve) {
        new AlertDialog.Builder(this).setTitle("Review irreversible KOIN burn")
            .setMessage(BurnKoin.review(snapshot,amount,approve)).setNegativeButton("Cancel",null)
            .setPositiveButton("Review in KOIN Vault",(d,w)->walletAction("KOIN burn requested",c->c.burnKoin(snapshot,amount,approve,()->{
                String account=permissionAccount(true);LocalRpc rpc=new LocalRpc();
                BurnKoin.Snapshot current=BurnKoin.read(rpc::call,account,System.currentTimeMillis());
                if(!account.equals(permissionAccount(true)))throw new IllegalStateException("The node context changed. Review the burn again.");
                BurnKoin.requireCurrent(snapshot,current,amount,approve,System.currentTimeMillis());
            }),true,true)).show();
    }
    private interface KeyTask { String run(String n) throws Exception; }
    private void keyTask(String title, KeyTask action) { keyTask(title,action,false); }
    private void keyTask(String title, KeyTask action, boolean sensitive) {
        if (keyBusy) return; String chosen = network; keyBusy = true; refresh();
        work.execute(() -> { String result; boolean success = true; try { result = action.run(chosen); } catch (Exception | LinkageError e) { success = false; result = "Could not complete: " + e.getMessage() + "\nExisting key data was preserved."; }
            if(success)HistoryStore.record(this,chosen,title,"Completed on this device. Private key material is never included in history.");
            String finalResult = result, finalTitle = success ? title : "Action could not complete"; handler.post(() -> { keyBusy = false; if (!isDestroyed()) { message(finalTitle, finalResult, sensitive); refresh(); } }); });
    }
    private void createKey() { keyTask("Signing key created", n -> { byte[] pub = new KeyVault(this, n).create(); return "Back up the signing key before continuing. On Mainnet, use Register signing key with KOIN Vault for passkey approval. Public key on " + n + ":\n\n" + LocalRpc.b64(pub); }); }
    private void importKey() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Import block-signing WIF").setMessage("Use only the node's signing key, not your funds wallet key or recovery phrase.")
            .setView(input).setNegativeButton("Cancel", (d, w) -> input.setText(""))
            .setPositiveButton("Import key", (d, w) -> { String wif = input.getText().toString(); input.setText(""); keyTask("Signing key import", n -> { byte[] pub = new KeyVault(this, n).importWif(wif); return "Key saved. Public key:\n\n" + LocalRpc.b64(pub); }); }).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(d->input.setText(""));
        dialog.show();
    }
    private void showPublicKey() {
        keyTask("Block-signing public key", n -> { String pub = LocalRpc.b64(new KeyVault(this, n).publicKey()); handler.post(() -> {
            if (!isDestroyed()) ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Teleno public signing key · " + n, pub));
        }); return pub + "\n\nOnly this public key was copied. Register it through your wallet on " + n + "."; });
    }
    private void backupKey() {
        new AlertDialog.Builder(this).setTitle("Show private block-signing key?").setMessage("Anyone with this key can sign blocks for your registered producer. Keep an offline copy. It will be shown privately, without copying it to the clipboard. This is not a funds-wallet backup.")
            .setNegativeButton("Cancel", null).setPositiveButton("Show privately", (d, w) -> keyTask("Private signing key · keep offline", n -> new KeyVault(this, n).exportWif(), true)).show();
    }
    private void reviewProduction() {
        NodeService.ViewState current = NodeService.snapshot();
        ProductionSetup setup = productionSetup(current);
        if (setup.action != ProductionSetup.Action.REVIEW) { explainProduction(setup, current); return; }
        final String chosen = network, networkLabel = label();
        final String account = getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + chosen, "");
        keyBusy = productionReviewLoading = true; refresh();
        productionReviewWork.execute(() -> {
            try {
                final byte[] publicKey = new KeyVault(this, chosen).publicKey();
                handler.post(() -> {
                    keyBusy = productionReviewLoading = false; if (isDestroyed()) return; refresh();
                    if (!chosen.equals(network)) { message("Review again", "The selected network changed. Tap Review again."); return; }
                    NodeService.ViewState now = NodeService.snapshot();
                    ProductionSetup latest = productionSetup(now);
                    if (latest.action != ProductionSetup.Action.REVIEW) { explainProduction(latest, now); return; }
                    new AlertDialog.Builder(this).setTitle("Start block production on " + networkLabel + "?")
                        .setMessage("Network: " + networkLabel + "\nProducer: " + account + "\nPublic signing key: " + LocalRpc.b64(publicKey)
                            + "\n\nAutomatic recovery will resume this same producer after temporary interruptions or reboot, following fresh observer, key and VHP checks. Stop node cancels automatic recovery.\n\nThis enables real block signing on the selected network. Keep the phone on power and stop any other node using this key. The app will recheck sync, VHP and registration, stop the observer cleanly, then start production. Use Stop node to stop production. Successful blocks consume VHP for KOIN rewards. This start action does not submit a separate token transaction.")
                        .setNegativeButton("Keep observer", null).setPositiveButton("Check and start production", (d, w) -> {
                            if (!chosen.equals(network) || !account.equals(getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + chosen, ""))) { message("Review again", "The network or producer account changed. Tap Review again to confirm the current account."); return; }
                            NodeService.ViewState node = NodeService.snapshot();
                            ProductionSetup startSetup = productionSetup(node);
                            if (startSetup.action != ProductionSetup.Action.REVIEW) { explainProduction(startSetup, node); return; }
                            startForegroundService(new Intent(this, NodeService.class).setAction(NodeService.PRODUCER).putExtra("network", chosen)
                                .putExtra(NodeService.REVIEWED, true).putExtra(NodeService.REVIEW_ACCOUNT, account).putExtra(NodeService.REVIEW_KEY, publicKey));
                        }).show();
                });
            } catch (Exception e) { handler.post(() -> { keyBusy = productionReviewLoading = false; if (!isDestroyed()) { refresh(); message("Production review unavailable", e.getMessage()); } }); }
        });
    }
    private ProductionSetup productionSetup(NodeService.ViewState v) {
        ProductionSetup.State s = new ProductionSetup.State();
        s.engine = engineAvailable; s.productionBuild = ProductionReview.buildAllowsProduction(engineAvailable, productionQualified, experimentalProduction);
        s.active = v.active;
        boolean pendingPermission=walletClient!=null&&walletClient.view().pending&&VaultClient.ALLOWANCE.equals(walletClient.view().kind);
        s.busy = v.busy || snapshotBusy || permissionBusy || burnBusy || pendingPermission || (walletClient!=null&&walletClient.view().pending&&VaultClient.BURN.equals(walletClient.view().kind));
        s.phase = pendingPermission ? "Check VHP permission in step 5 to resolve the saved allowance request" : permissionBusy ? "Checking VHP permission" : snapshotBusy ? "Checking chain snapshot" : v.phase;
        s.observer = NodeService.OBSERVER.equals(v.mode); s.producer = NodeService.PRODUCER.equals(v.mode); s.sameNetwork = network.equals(v.network);
        s.ready = v.ready; s.readiness = v.readiness; s.networkLabel = label(); s.keyBusy = keyBusy; s.reviewLoading = productionReviewLoading;
        s.hasAccount = !getSharedPreferences("settings", MODE_PRIVATE).getString("producer-" + network, "").isEmpty();
        s.hasKey = new KeyVault(this, network).exists(); s.savedDownload = SnapshotBootstrap.pending(this, network);
        return ProductionSetup.from(s);
    }
    private String productionProblem(NodeService.ViewState v) {
        return network.equals(v.productionProblemNetwork) && !v.productionProblem.isEmpty() ? "\n\nLast production start check: " + v.productionProblem : "";
    }
    private void explainProduction(ProductionSetup setup, NodeService.ViewState v) {
        String report = "App " + appVersion() + "\nNetwork: " + label() + "\n\n" + setup.note;
        if (network.equals(v.network) && v.hasSample) report += "\n\nLocal block: " + v.height + "\nIrreversible block: " + v.lib + "\nConnected peers: " + (v.peers < 0 ? "waiting for report" : v.peers);
        report += productionProblem(v);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Production setup status").setMessage(report).setNegativeButton("Close", null);
        if (setup.action == ProductionSetup.Action.START_OBSERVER) {
            final String chosen = network;
            dialog.setPositiveButton("Start observer", (d, w) -> {
                NodeService.ViewState now = NodeService.snapshot();
                ProductionSetup latest = productionSetup(now);
                if (chosen.equals(network) && latest.action == ProductionSetup.Action.START_OBSERVER) runNode(NodeService.OBSERVER);
                else explainProduction(latest, now);
            });
        }
        dialog.show();
    }
    private void showNotices() {
        try {
            String[] files = getAssets().list("licenses");
            if (files == null || files.length == 0) throw new java.io.IOException("No notices are available.");
            Arrays.sort(files);
            new AlertDialog.Builder(this).setTitle("Open-source notices").setItems(files, (d, which) -> {
                try { message(files[which], new String(NodeConfig.asset(this, "licenses/" + files[which]), StandardCharsets.UTF_8)); }
                catch (Exception e) { message("Notice unavailable", e.getMessage()); }
            }).setNegativeButton("Done", null).show();
        } catch (Exception e) { message("Notices unavailable", e.getMessage()); }
    }
    private void message(String title, String body) { message(title,body,false); }
    private void message(String title, String body, boolean sensitive) {
        TextView t = text(body == null ? "Unavailable" : body, 15, INK, false); t.setTextIsSelectable(!sensitive); t.setPadding(dp(22), dp(16), dp(22), dp(16));
        ScrollView box = new ScrollView(this); box.addView(t);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Done", null).create();
        if(sensitive){
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.setOnDismissListener(d->t.setText(""));
        }
        dialog.show();
        if(sensitive)handler.postDelayed(() -> { if (dialog.isShowing()) dialog.dismiss(); t.setText(""); }, 60000);
    }
}
