package pl.odkrywca.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

import java.util.List;

/**
 * Most między interfejsem (JavaScript) a usługą nagrywania trasy w tle.
 */
@CapacitorPlugin(
    name = "Odkrywca",
    permissions = {
        @Permission(alias = "location", strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }),
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
    }
)
public class OdkrywcaPlugin extends Plugin {

    private static OdkrywcaPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    @Override
    protected void handleOnDestroy() {
        if (instance == this) instance = null;
    }

    /* ---------- zdarzenia z usługi ---------- */

    static void emitLocation() {
        OdkrywcaPlugin p = instance;
        if (p != null) p.notifyListeners("location", new JSObject());
    }

    static void emitStatus(boolean tracking) {
        OdkrywcaPlugin p = instance;
        if (p == null) return;
        JSObject o = new JSObject();
        o.put("tracking", tracking);
        p.notifyListeners("status", o);
    }

    /* ---------- metody dostępne z JS ---------- */

    @PluginMethod
    public void start(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "locationPermsCallback");
            return;
        }
        askNotificationsThenStart(call);
    }

    @PermissionCallback
    private void locationPermsCallback(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            call.reject("Brak zgody na lokalizację", "PERMISSION_DENIED");
            return;
        }
        askNotificationsThenStart(call);
    }

    private void askNotificationsThenStart(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") == PermissionState.PROMPT) {
            requestPermissionForAlias("notifications", call, "notificationPermsCallback");
            return;
        }
        doStart(call);
    }

    @PermissionCallback
    private void notificationPermsCallback(PluginCall call) {
        // powiadomienie nie jest konieczne do działania – startujemy niezależnie od odpowiedzi
        doStart(call);
    }

    private void doStart(PluginCall call) {
        Context ctx = getContext();
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsOn = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        try {
            TrackingService.start(ctx);
        } catch (Exception e) {
            call.reject("Nie udało się uruchomić usługi: " + e.getMessage());
            return;
        }
        JSObject r = new JSObject();
        r.put("tracking", true);
        r.put("gpsEnabled", gpsOn);
        call.resolve(r);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        TrackingService.stop(getContext());
        JSObject r = new JSObject();
        r.put("tracking", false);
        call.resolve(r);
    }

    @PluginMethod
    public void status(PluginCall call) {
        Context ctx = getContext();
        JSObject r = new JSObject();
        r.put("tracking", TrackingService.isRunning());
        r.put("pending", PointStore.count(ctx));
        r.put("batteryUnrestricted", isIgnoringBatteryOptimizations());
        r.put("locationPermission", getPermissionState("location").toString());
        call.resolve(r);
    }

    @PluginMethod
    public void drain(PluginCall call) {
        List<double[]> pts = PointStore.drain(getContext());
        JSArray arr = new JSArray();
        for (double[] p : pts) {
            JSArray a = new JSArray();
            try {
                for (double v : p) a.put(v);
            } catch (JSONException e) {
                continue; // wartości nieskończone – pomijamy punkt
            }
            arr.put(a);
        }
        JSObject r = new JSObject();
        r.put("points", arr);
        call.resolve(r);
    }

    @SuppressLint("BatteryLife")
    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        Context ctx = getContext();
        try {
            Intent i;
            if (!isIgnoringBatteryOptimizations()) {
                i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName()));
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName()));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        }
        call.resolve();
    }

    @PluginMethod
    public void openLocationSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }
}
