package com.koinos.teleno;

import android.content.*;

/** Resume only the run explicitly started by the user; never submit wallet actions. */
public final class NodeRestartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent==null?"":intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        if(!NodeService.resumeRequested(context))return;
        try { context.startForegroundService(new Intent(context,NodeService.class).setAction(NodeService.RECOVER)); }
        catch(RuntimeException e) { NodeService.saveReason(context,"Android delayed automatic recovery. Open Teleno to resume the saved run."); }
    }
}
