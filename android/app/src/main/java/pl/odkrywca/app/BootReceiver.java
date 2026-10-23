package pl.odkrywca.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wznawia zapis trasy po włączeniu telefonu i po aktualizacji aplikacji. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
            || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            TrackingService.ensureRunning(context);
        }
    }
}
