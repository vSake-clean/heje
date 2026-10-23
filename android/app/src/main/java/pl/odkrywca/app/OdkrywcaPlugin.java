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
 * Most między interfejsem (JavaScript) a usługą zapisu trasy. Celowo nie ma metody
 * zatrzymującej zapis: aplikacja działa cały czas.
 */
@CapacitorPlugin(
    name = "Odkrywca",
    permissions = {
        @Permission(alias = "location", strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }),
        @Permission(alias = "background", strings = { Manifest.permission.ACCESS_BACKGROUND_LOCATION }),
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

    /** Prosi o zgodę na lokalizację (i powiadomienia), po czym uruchamia zapis. */
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
        doStart(call);
    }

    private void doStart(PluginCall call) {
        TrackingService.ensureRunning(getContext());
        call.resolve(statusObject());
    }

    /** Zgoda "zawsze" pozwala wznowić zapis po restarcie telefonu bez otwierania aplikacji. */
    @PluginMethod
    public void requestBackground(PluginCall call) {
        if (hasBackgroundPermission()) {
            call.resolve(statusObject());
            return;
        }
        requestPermissionForAlias("background", call, "backgroundPermsCallback");
    }

    @PermissionCallback
    private void backgroundPermsCallback(PluginCall call) {
        call.resolve(statusObject());
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(statusObject());
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
                continue; // wartości nieskończone, pomijamy punkt
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
        Intent i = isIgnoringBatteryOptimizations()
            ? new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName()))
            : new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName()));
        startSafely(i);
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())));
        call.resolve();
    }

    @PluginMethod
    public void openLocationSettings(PluginCall call) {
        startSafely(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        call.resolve();
    }

    /* ---------- pomocnicze ---------- */

    private JSObject statusObject() {
        Context ctx = getContext();
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        JSObject r = new JSObject();
        r.put("tracking", TrackingService.isRunning());
        r.put("location", getPermissionState("location") == PermissionState.GRANTED);
        r.put("background", hasBackgroundPermission());
        r.put("gpsEnabled", lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        r.put("batteryUnrestricted", isIgnoringBatteryOptimizations());
        return r;
    }

    private boolean hasBackgroundPermission() {
        // przed Androidem 10 zgoda na lokalizację obejmowała też działanie w tle
        if (Build.VERSION.SDK_INT < 29) return getPermissionState("location") == PermissionState.GRANTED;
        return getPermissionState("background") == PermissionState.GRANTED;
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }

    private void startSafely(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(i);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(fallback);
        }
    }
}
