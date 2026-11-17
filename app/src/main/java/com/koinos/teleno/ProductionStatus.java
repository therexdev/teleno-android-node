package com.koinos.teleno;

import java.util.Locale;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only native telemetry. Never retain or display raw engine output. */
final class ProductionStatus {
    static final long FRESH_MS = 90000;
    private static final Pattern COLOR = Pattern.compile("\u001b\\[[0-9;]*m");
    private static final Pattern HEADER = Pattern.compile(
        "^(?:[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6} )?"
        + "\\([^()\\r\\n]+\\) \\[[^\\]\\r\\n]+:[0-9]+\\] <(info|warning|error|fatal)>: (.*)$");
    private static final Pattern RUNNING = Pattern.compile("\\bproducer_running=(true|false|1|0)(?=\\s|$)");
    private static final Pattern BLOCK = Pattern.compile("^\\[block_producer\\] Produced block #[0-9]+ at height ([0-9]+)$");
    private static final String[] COUNTERS = {"producer_pob_evaluations", "producer_candidates_prepared",
        "producer_errors", "producer_head_retries", "producer_mempool_retries"};
    private static final Pattern[] METRICS = new Pattern[COUNTERS.length];
    static { for (int i=0;i<COUNTERS.length;i++) METRICS[i]=Pattern.compile("(?:^|\\s)"+COUNTERS[i]+"=([0-9]+)(?=\\s|$)"); }
    private boolean running;
    private int gate=-1;
    private long blocks,lastHeight,metricsAt,interval,warningAt;
    private long evaluations=-1,candidates=-1,errors=-1,headRetries=-1,mempoolRetries=-1,delta=-1,errorDelta=-1;
    private String warning="";
    private String warningCode="NONE";
    private final ArrayList<WarningEvent> warnings=new ArrayList<>();

    synchronized void reset() {
        running=false;gate=-1;blocks=lastHeight=metricsAt=interval=warningAt=0;
        evaluations=candidates=errors=headRetries=mempoolRetries=delta=errorDelta=-1;warning="";warningCode="NONE";warnings.clear();
    }
    synchronized boolean running(){return running;}
    synchronized long blocks(){return blocks;}
    synchronized long lastHeight(){return lastHeight;}
    synchronized Snapshot snapshot(){return new Snapshot(this);}
    synchronized boolean accept(String line){return accept(line,System.nanoTime()/1000000L);}
    synchronized boolean accept(String line,long now) {
        if(line==null||line.length()>8192)return false;
        String plain=COLOR.matcher(line).replaceAll("");String body,level;
        Matcher header=HEADER.matcher(plain);
        if(header.matches()){level=header.group(1);body=header.group(2);}
        // Plain prefixes are also used by the native-status host fixtures.
        else if(plain.startsWith("INFO ")){level="info";body=plain.substring(5);}
        else if(plain.startsWith("WARNING ")){level="warning";body=plain.substring(8);}
        else return false;
        if(body.equals("[block_producer] Production loop started"))running=true;
        if(body.matches("\\[block_producer\\] Stopped \\(produced [0-9]+ blocks\\)"))running=false;
        if(body.equals("[block_producer] Gossip production gate opened")||body.equals("[block_producer] Gossip production gate: disabled"))gate=1;
        if(body.equals("[block_producer] Gossip production gate closed"))gate=0;
        if(body.startsWith("[block_producer] ")&&!level.equals("info")) {
            WarningKind kind=warningCategory(body.toLowerCase(Locale.ROOT));
            warningAt=now;warning=kind.message;warningCode=kind.name();
            int last=warnings.size()-1;
            if(last>=0&&warnings.get(last).kind==kind) {
                WarningEvent previous=warnings.get(last);
                warnings.set(last,new WarningEvent(kind,now,previous.count==Long.MAX_VALUE?Long.MAX_VALUE:previous.count+1));
            } else {
                if(warnings.size()==8)warnings.remove(0);
                warnings.add(new WarningEvent(kind,now,1));
            }
        }
        if(level.equals("info")&&body.startsWith("[metrics] ")) {
            Matcher active=RUNNING.matcher(body);
            boolean valid=active.find();
            if(valid){boolean value="1".equals(active.group(1))||"true".equals(active.group(1));valid=!active.find();if(valid)running=value;}
            long[] values=new long[METRICS.length];
            for(int i=0;i<METRICS.length;i++) {
                Matcher m=METRICS[i].matcher(body);
                if(!m.find()){valid=false;break;}
                try{values[i]=Long.parseLong(m.group(1));}catch(NumberFormatException e){valid=false;break;}
                if(m.find()){valid=false;break;}
            }
            if(valid&&now>0) {
                boolean baseline=metricsAt>0&&now>metricsAt&&now-metricsAt<=FRESH_MS&&values[0]>=evaluations&&values[2]>=errors;
                interval=baseline?now-metricsAt:0;delta=baseline?values[0]-evaluations:-1;errorDelta=baseline?values[2]-errors:-1;
                evaluations=values[0];candidates=values[1];errors=values[2];headRetries=values[3];mempoolRetries=values[4];metricsAt=now;
            }
        }
        Matcher block=BLOCK.matcher(body);
        if(level.equals("info")&&block.matches())try {
            long height=Long.parseLong(block.group(1));
            if(height>0){blocks++;lastHeight=height;return true;}
        }catch(NumberFormatException ignored){}
        return false;
    }
    private enum WarningKind {
        VHP_BURN_REJECTED("A candidate block was rejected because its VHP burn failed. This can involve smart-account authorization; it does not by itself mean the wallet needs more VHP. For KOIN Vault, stop production, start an observer, then check VHP permission in Setup step 5. Copy this report before changing permissions."),
        VHP_BALANCE_ZERO("The local engine read zero effective VHP for the configured producer. Compare the local balance and network with the wallet before changing funds."),
        VHP_BALANCE_DECODE("The engine could not decode the effective-VHP response. This is a contract-read error, not proof of an empty balance."),
        VHP_DECIMALS_DECODE("The engine could not decode the VHP decimals response."),
        VHP_DECIMALS_INVALID("The engine read an invalid VHP decimals value from the local chain."),
        VHP_SYMBOL_DECODE("The engine could not decode the VHP symbol response."),
        VHP_CONTRACT_LOOKUP("The engine could not decode the VHP contract address."),
        TOKEN_BALANCE_READ_FAILED("The consensus contract could not retrieve a token balance."),
        PRODUCER_KEY_MISSING("The consensus contract found no public-key record for the producer."),
        PRODUCER_KEY_NOT_ACTIVE("The registered production key is not active yet. Let the chain advance before checking again."),
        PRODUCER_VRF_REJECTED("The candidate's VRF proof failed validation against the registered production key."),
        PRODUCER_PROOF_REJECTED("The consensus contract rejected the candidate's eligibility proof."),
        POB_PARAMETERS_DECODE("The engine could not decode the PoB consensus parameters."),
        POB_METADATA_DECODE("The engine could not decode PoB metadata."),
        POB_TIMING_INVALID("The engine read invalid PoB timing parameters."),
        POB_DIFFICULTY_ZERO("The engine read zero PoB difficulty."),
        MANA_OR_RESOURCES("The engine reported insufficient mana or block resources."),
        SIGNING_OR_KEY("The engine reported a signing or registered-key failure."),
        VHP_OTHER("The engine reported another VHP-related error. This category does not establish that the wallet has insufficient VHP."),
        STORAGE_FULL("The engine reported full storage."),
        DATABASE_OR_STORAGE("The engine reported a database or storage problem."),
        CHAIN_HEAD("The engine reported a changing or unavailable chain head."),
        OTHER("The native producer reported an unclassified warning. Check whether errors continue to increase.");
        final String message;WarningKind(String message){this.message=message;}
    }
    private static final class WarningEvent {
        final WarningKind kind;final long at,count;
        WarningEvent(WarningKind kind,long at,long count){this.kind=kind;this.at=at;this.count=count;}
    }
    private static WarningKind warningCategory(String body) {
        // Only fixed, source-grounded labels survive. Never retain the exception text.
        if(body.contains("could not burn vhp"))return WarningKind.VHP_BURN_REJECTED;
        if(body.contains("configured producer has no effective vhp balance"))return WarningKind.VHP_BALANCE_ZERO;
        if(body.contains("unable to parse vhp balance"))return WarningKind.VHP_BALANCE_DECODE;
        if(body.contains("unable to parse vhp decimals"))return WarningKind.VHP_DECIMALS_DECODE;
        if(body.contains("invalid vhp decimals from chain"))return WarningKind.VHP_DECIMALS_INVALID;
        if(body.contains("unable to parse vhp symbol"))return WarningKind.VHP_SYMBOL_DECODE;
        if(body.contains("unable to parse contract address for vhp"))return WarningKind.VHP_CONTRACT_LOOKUP;
        if(body.contains("failed to retrieve token balance"))return WarningKind.TOKEN_BALANCE_READ_FAILED;
        if(body.contains("signer address has no public key record"))return WarningKind.PRODUCER_KEY_MISSING;
        if(body.contains("public key not yet active"))return WarningKind.PRODUCER_KEY_NOT_ACTIVE;
        if(body.contains("proof failed vrf validation"))return WarningKind.PRODUCER_VRF_REJECTED;
        if(body.contains("provided hash is not sufficient"))return WarningKind.PRODUCER_PROOF_REJECTED;
        if(body.contains("unable to parse pob consensus parameters"))return WarningKind.POB_PARAMETERS_DECODE;
        if(body.contains("unable to parse pob metadata"))return WarningKind.POB_METADATA_DECODE;
        if(body.contains("invalid pob consensus timing")||body.contains("invalid pob quanta per block interval"))return WarningKind.POB_TIMING_INVALID;
        if(body.contains("pob difficulty is zero"))return WarningKind.POB_DIFFICULTY_ZERO;
        if(body.contains("insufficient")&&(body.contains("mana")||body.contains("resource")||body.contains("rc")))return WarningKind.MANA_OR_RESOURCES;
        if(body.contains("public key")||body.contains("signature"))return WarningKind.SIGNING_OR_KEY;
        if(body.contains("vhp"))return WarningKind.VHP_OTHER;
        if(body.contains("no space left")||body.contains("disk full"))return WarningKind.STORAGE_FULL;
        if(body.contains("i/o error")||body.contains("corruption")||body.contains("rocksdb"))return WarningKind.DATABASE_OR_STORAGE;
        if(body.contains("head")||body.contains("parent"))return WarningKind.CHAIN_HEAD;
        return WarningKind.OTHER;
    }
    enum Health { WAITING, STALE, PAUSED, WARNING, BASELINE, ACTIVE, NO_PROGRESS }
    static final class Snapshot {
        final boolean running;final int gate;
        final long metricsAt,interval,evaluations,candidates,errors,headRetries,mempoolRetries,delta,errorDelta,warningAt;
        final String warning,warningCode;
        private final WarningEvent[] warnings;
        private Snapshot(ProductionStatus s) {
            running=s.running;gate=s.gate;metricsAt=s.metricsAt;interval=s.interval;evaluations=s.evaluations;candidates=s.candidates;
            errors=s.errors;headRetries=s.headRetries;mempoolRetries=s.mempoolRetries;delta=s.delta;errorDelta=s.errorDelta;
            warning=s.warning;warningAt=s.warningAt;warningCode=s.warningCode;warnings=s.warnings.toArray(new WarningEvent[0]);
        }
        private boolean recent(long at,long now){return at>0&&now>=at&&now-at<=FRESH_MS;}
        Health health(long now) {
            if(recent(warningAt,now))return Health.WARNING;
            if(gate==0)return Health.PAUSED;
            if(metricsAt==0)return Health.WAITING;
            if(!recent(metricsAt,now))return Health.STALE;
            if(!running)return Health.PAUSED;
            if(errorDelta>0)return Health.WARNING;
            if(delta<0)return Health.BASELINE;
            return delta>0?Health.ACTIVE:Health.NO_PROGRESS;
        }
        boolean advancing(long now){return health(now)==Health.ACTIVE;}
        String title(long now) {
            switch(health(now)) {
                case ACTIVE:return "Checking block opportunities";
                case PAUSED:return "Production is paused";
                case WARNING:return "Producer needs attention";
                case STALE:return "Production telemetry is stale";
                case NO_PROGRESS:return "No new block checks";
                case BASELINE:return "Measuring production activity";
                default:return "Waiting for production telemetry";
            }
        }
        String explanation(long now) {
            switch(health(now)) {
                case ACTIVE:return "Block eligibility checks are advancing. The node is waiting for a winning opportunity; a reward is not guaranteed.";
                case PAUSED:return gate==0?"The native peer-sync gate is closed. Let the node catch up and check its peers.":"The native engine reports that its production loop is paused.";
                case WARNING:return recent(warningAt,now)?warning:"The engine's error counter increased in the latest interval. Open Production health for the counts.";
                case STALE:return "No fresh native telemetry arrived in 90 seconds. This screen cannot confirm that block checks are still running.";
                case NO_PROGRESS:return "The native loop is open, but no block eligibility checks completed between the last two reports. Check retries, peers and local block age below.";
                case BASELINE:return "The first counters arrived. Wait for the next report to see whether they advance (normally 60 seconds).";
                default:return "The engine normally reports counters every 60 seconds. Wait up to two minutes after starting for a measured activity rate.";
            }
        }
        String brief(long now) {
            if(metricsAt==0)return "Waiting for native counters · normally every 60 seconds";
            return "Block checks: "+number(evaluations)+(delta>=0?" · +"+number(delta)+" in "+Math.max(1,interval/1000)+"s":"")
                +"\nErrors: "+number(errors)+" · report "+age(metricsAt,now);
        }
        String report(long now) {
            return title(now)+"\n"+explanation(now)+"\n\nNative loop: "+(running?"running":"not running")
                +"\nPeer-sync gate: "+(gate<0?"not reported":gate==0?"closed":"open")
                +"\nBlock eligibility checks: "+number(evaluations)
                +"\nChecks in latest interval: "+number(delta)+(interval>0?" over "+(interval/1000)+"s":"")
                +"\nChecks per second: "+(delta>=0&&interval>0?String.format(Locale.ROOT,"%.1f",delta*1000.0/interval):"waiting")
                +"\nCandidates prepared: "+number(candidates)+"\nHead retries: "+number(headRetries)
                +"\nMempool retries: "+number(mempoolRetries)+"\nEngine errors this run: "+number(errors)
                +"\nErrors in latest interval: "+number(errorDelta)+"\nCounter report: "+age(metricsAt,now)
                +(warningAt>0?"\nLast warning code: "+warningCode+"\nLast warning: "+warning+" ("+age(warningAt,now)+")":"")
                +warningHistory(now)
                +"\n\nCounters come from the local engine. A block check is an eligibility attempt, not a produced block. Head retries can occur normally when another block arrives.";
        }
        private String warningHistory(long now) {
            if(warnings.length==0)return "";
            StringBuilder text=new StringBuilder("\n\nRecent warning groups (newest first; up to 8):");
            for(int i=warnings.length-1;i>=0;i--) {
                WarningEvent e=warnings[i];text.append("\n").append(e.kind.name()).append(" · ").append(number(e.count)).append(" occurrence(s) · last ").append(age(e.at,now));
            }
            return text.toString();
        }
        private static String number(long n){return n<0?"waiting":java.text.NumberFormat.getIntegerInstance().format(n);}
        private static String age(long at,long now){return at<=0?"not received":now<at?"unavailable":((now-at)/1000)+"s ago";}
    }
}
