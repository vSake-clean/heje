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
 * Usługa pierwszoplanowa, która cały czas zapisuje pozycję GPS. Nie ma przycisku
 * zatrzymania: działa od pierwszego uruchomienia aplikacji, wraca po restarcie
 * telefonu i po aktualizacji. Punkty trafiają do {@link PointStore}.
 */
public class TrackingService extends Service implements LocationListener {

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

    /** Uruchamia usługę, jeśli są uprawnienia. Wywołanie, gdy już działa, nic nie zmienia. */
    static void ensureRunning(Context ctx) {
        if (!hasLocationPermission(ctx)) return;
        try {
            ContextCompat.startForegroundService(ctx, new Intent(ctx, TrackingService.class));
        } catch (Exception ignored) {
            // system nie pozwolił wystartować z tła; spróbujemy przy następnym otwarciu aplikacji
        }
    }

    static boolean hasLocationPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasLocationPermission(this)) {
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
            // bez zgody "zawsze" Android nie pozwala wystartować z tła; aplikacja uruchomi usługę po otwarciu
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            running = true;
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
            // sieć jako zapas, gdy GPS nie ma sygnału; używana tylko bez świeżego odczytu GPS
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
        // odrzucamy pozycje z aplikacji do podrabiania lokalizacji
        if (isMock(loc)) return;

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

        if (now - lastNotificationUpdate > 15000) {
            lastNotificationUpdate = now;
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isMock(Location loc) {
        if (Build.VERSION.SDK_INT >= 31) return loc.isMock();
        return loc.isFromMockProvider();
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
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Zapis trasy", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Informuje, że Odkrywca zapisuje odwiedzone miejsca");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);

        String text = sessionPoints == 0
            ? "Czekam na sygnał GPS"
            : "Od włączenia telefonu: " + formatDistance(sessionMeters);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentTitle("Zapis trasy włączony")
            .setContentText(text)
            .setColor(0xFF2F5D50)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPi)
            .build();
    }

    private static String formatDistance(double m) {
        if (m < 1000) return Math.round(m) + " m";
        return String.format(Locale.forLanguageTag("pl-PL"), "%.1f km", m / 1000.0);
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
