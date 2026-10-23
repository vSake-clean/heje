package pl.odkrywca.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Usługa pierwszoplanowa nagrywająca pozycję GPS także wtedy, gdy aplikacja jest
 * zminimalizowana, a ekran wyłączony. Punkty trafiają do {@link PointStore}.
 */
public class TrackingService extends Service implements LocationListener {

    static final String ACTION_START = "pl.odkrywca.app.START";
    static final String ACTION_STOP = "pl.odkrywca.app.STOP";
    static final String PREFS = "odkrywca";
    static final String PREF_TRACKING = "tracking";

    private static final String CHANNEL_ID = "tracking";
    private static final int NOTIFICATION_ID = 1907;
    private static final long MIN_TIME_MS = 2000;
    private static final float MIN_DISTANCE_M = 4f;
    private static final float MAX_ACCURACY_M = 100f;

    private static volatile boolean running = false;

    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private Location lastGood;
    private double sessionMeters = 0;
    private int sessionPoints = 0;
    private long lastNotificationUpdate = 0;
    private long lastGpsFix = 0;

    static boolean isRunning() {
        return running;
    }

    static void start(Context ctx) {
        Intent i = new Intent(ctx, TrackingService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(ctx, i);
    }

    static void stop(Context ctx) {
        prefs(ctx).edit().putBoolean(PREF_TRACKING, false).apply();
        ctx.stopService(new Intent(ctx, TrackingService.class));
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stop(this);
            OdkrywcaPlugin.emitStatus(false);
            return START_NOT_STICKY;
        }

        // intent == null oznacza wznowienie przez system po zabiciu procesu
        if (intent == null && !prefs(this).getBoolean(PREF_TRACKING, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!hasLocationPermission()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        } catch (Exception e) {
            // Android może odmówić startu usługi lokalizacji z tła – wtedy użytkownik musi otworzyć aplikację
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            running = true;
            prefs(this).edit().putBoolean(PREF_TRACKING, true).apply();
            acquireWakeLock();
            requestUpdates();
            OdkrywcaPlugin.emitStatus(true);
        }
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void requestUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        Looper looper = Looper.getMainLooper();
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, this, looper);
        } catch (Exception ignored) {
            // brak dostawcy GPS
        }
        try {
            // sieć jako zapas, gdy GPS nie ma sygnału (np. w budynku) – używana tylko bez świeżego GPS
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 20f, this, looper);
        } catch (Exception ignored) {
            // brak dostawcy sieciowego
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        boolean isGps = LocationManager.GPS_PROVIDER.equals(loc.getProvider());
        long now = System.currentTimeMillis();
        if (isGps) lastGpsFix = now;
        else if (now - lastGpsFix < 30000) return;

        if (!loc.hasAccuracy() || loc.getAccuracy() > MAX_ACCURACY_M) return;

        if (lastGood != null) {
            float d = lastGood.distanceTo(loc);
            if (d < MIN_DISTANCE_M) return;
            double dt = Math.max(1, (loc.getTime() - lastGood.getTime()) / 1000.0);
            if (d / dt < 85) sessionMeters += d;
        }
        lastGood = loc;
        sessionPoints++;

        PointStore.append(this, loc.getTime(), loc.getLatitude(), loc.getLongitude(),
            loc.getAccuracy(), loc.hasBearing() ? loc.getBearing() : -1f);
        OdkrywcaPlugin.emitLocation();

        if (now - lastNotificationUpdate > 10000) {
            lastNotificationUpdate = now;
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {}

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Odkrywanie w tle", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Informuje, że Odkrywca zapisuje Twoją trasę");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);
        Intent stopIntent = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags);

        String text = sessionPoints == 0
            ? "Szukam sygnału GPS…"
            : String.format(Locale.forLanguageTag("pl-PL"), "Przebyto %s · mapa odsłania się w tle", formatDistance(sessionMeters));

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentTitle("🧭 Odkrywanie aktywne")
            .setContentText(text)
            .setColor(0xFFFF7A59)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPi)
            .addAction(0, "Zatrzymaj", stopPi)
            .build();
    }

    private static String formatDistance(double m) {
        if (m < 1000) return Math.round(m) + " m";
        return String.format(Locale.forLanguageTag("pl-PL"), "%.2f km", m / 1000.0);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Odkrywca::Tracking");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(); // zwalniany w onDestroy
    }

    @Override
    public void onDestroy() {
        running = false;
        if (locationManager != null) locationManager.removeUpdates(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        OdkrywcaPlugin.emitStatus(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
