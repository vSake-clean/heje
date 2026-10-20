package pl.heje.ekran;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Przechwytuje ekran (MediaProjection), koduje klatki do JPEG i wysyła je
 * jako strumień MJPEG po HTTP na porcie {@link #PORT}. Co sekundę rozgłasza
 * w sieci lokalnej pakiet UDP, dzięki któremu program na PC sam znajduje telefon.
 */
public class ScreenService extends Service {
    public static final int PORT = 8080;
    public static final int DISCOVERY_PORT = 8089;
    public static final String MAGIC = "EKRANTELEFONU";

    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    static final String ACTION_STOP = "pl.heje.ekran.STOP";

    private static final String CHANNEL = "ekran";
    private static final int MAX_SIDE = 1280;
    private static final int JPEG_QUALITY = 60;
    private static final long FRAME_MS_ACTIVE = 50;   // ~20 kl./s gdy ktoś ogląda
    private static final long FRAME_MS_IDLE = 1000;   // 1 kl./s gdy nikt nie ogląda
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    static volatile boolean running;

    private volatile boolean stopping;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private ServerSocket server;
    private DatagramSocket beacon;

    private DisplayManager displayManager;
    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;

    private int width, height;
    private int frameWidth;
    private Bitmap bitmap;
    private boolean dirty;
    private boolean encodeScheduled;
    private long lastEncode;
    private final ByteArrayOutputStream jpeg = new ByteArrayOutputStream(256 * 1024);

    private final Object frameLock = new Object();
    private byte[] frame;
    private long frameId;
    private final AtomicInteger clients = new AtomicInteger();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection != null) return START_NOT_STICKY;

        // Na Androidzie 10+ usługa musi być na pierwszym planie, zanim pobierzemy MediaProjection.
        startForegroundCompat();

        int code = intent.getIntExtra(EXTRA_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            projection = data == null ? null : mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        acquireLocks();
        startCapture();
        startServer();
        startBeacon();
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Udostępnianie ekranu", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ScreenService.class).setAction(ACTION_STOP), piFlags);
        b.setContentTitle("Ekran wysyłany na komputer")
         .setContentText("Dotknij, aby zobaczyć adres telefonu")
         .setSmallIcon(android.R.drawable.presence_video_online)
         .setContentIntent(open)
         .setOngoing(true)
         .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Zatrzymaj", stop);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, n);
        }
    }

    /**
     * Nie pozwala systemowi uśpić procesora ani Wi-Fi, gdy użytkownik korzysta z innych
     * aplikacji – bez tego część telefonów zwalnia lub zrywa strumień w tle.
     */
    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EkranNaPC:cpu");
            cpuLock.acquire();
        } catch (Exception ignored) {
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EkranNaPC:wifi");
            wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- przechwytywanie

    private void startCapture() {
        captureThread = new HandlerThread("capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        DisplayMetrics m = currentMetrics();
        createReader(m);

        // Android 14 wymaga zarejestrowania callbacku przed utworzeniem wirtualnego ekranu.
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, captureHandler);

        display = projection.createVirtualDisplay("EkranNaPC", width, height, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null,
                captureHandler);

        // Po obróceniu telefonu (np. film, gra w poziomie) zmieniamy rozmiar obrazu.
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        displayManager.registerDisplayListener(displayListener, captureHandler);
    }

    private DisplayMetrics currentMetrics() {
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        return m;
    }

    /** Ustala docelowy rozmiar obrazu i tworzy ImageReader. Zwraca false, gdy rozmiar się nie zmienił. */
    private boolean createReader(DisplayMetrics m) {
        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(m.widthPixels, m.heightPixels));
        int w = Math.round(m.widthPixels * scale) & ~1;
        int h = Math.round(m.heightPixels * scale) & ~1;
        if (reader != null && w == width && h == height) return false;
        width = w;
        height = h;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader r) {
                onImage(r);
            }
        }, captureHandler);
        return true;
    }

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY || display == null || stopping) return;
            ImageReader old = reader;
            DisplayMetrics m = currentMetrics();
            if (!createReader(m)) return;
            display.resize(width, height, m.densityDpi);
            display.setSurface(reader.getSurface());
            old.close();
        }

        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }
    };

    /** Wywoływane na wątku capture: kopiuje najnowszą klatkę do bitmapy. */
    private void onImage(ImageReader r) {
        if (r != reader) return; // klatka sprzed obrotu
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            Image.Plane plane = img.getPlanes()[0];
            int rowPixels = plane.getRowStride() / plane.getPixelStride();
            if (bitmap == null || bitmap.getWidth() != rowPixels || bitmap.getHeight() != img.getHeight()) {
                bitmap = Bitmap.createBitmap(rowPixels, img.getHeight(), Bitmap.Config.ARGB_8888);
            }
            bitmap.copyPixelsFromBuffer(plane.getBuffer());
            frameWidth = img.getWidth();
            dirty = true;
            scheduleEncode();
        } catch (Exception ignored) {
        } finally {
            if (img != null) img.close();
        }
    }

    private void scheduleEncode() {
        if (encodeScheduled) return;
        encodeScheduled = true;
        long interval = clients.get() > 0 ? FRAME_MS_ACTIVE : FRAME_MS_IDLE;
        long delay = Math.max(0, lastEncode + interval - SystemClock.uptimeMillis());
        captureHandler.postDelayed(encodeTask, delay);
    }

    private final Runnable encodeTask = new Runnable() {
        @Override
        public void run() {
            encodeScheduled = false;
            if (!dirty || bitmap == null || stopping) return;
            dirty = false;
            lastEncode = SystemClock.uptimeMillis();
            Bitmap src = bitmap.getWidth() == frameWidth
                    ? bitmap : Bitmap.createBitmap(bitmap, 0, 0, frameWidth, bitmap.getHeight());
            jpeg.reset();
            src.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg);
            if (src != bitmap) src.recycle();
            publish(jpeg.toByteArray());
        }
    };

    private void publish(byte[] data) {
        synchronized (frameLock) {
            frame = data;
            frameId++;
            frameLock.notifyAll();
        }
    }

    // ---------------------------------------------------------------- serwer MJPEG

    private void startServer() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
        } catch (IOException e) {
            stopSelf();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopping) {
                    try {
                        final Socket s = server.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                serve(s);
                            }
                        }, "client").start();
                    } catch (IOException e) {
                        if (stopping) return;
                    }
                }
            }
        }, "server").start();
    }

    private void serve(Socket s) {
        clients.incrementAndGet();
        try {
            s.setTcpNoDelay(true);
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), LATIN1));
            String request = in.readLine();
            if (request == null) return;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // nagłówki ignorujemy
            }
            s.setSoTimeout(0);
            String[] parts = request.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 64 * 1024);

            if (path.startsWith("/info")) {
                // Odpowiedź identyfikująca – program na PC szuka tak telefonu w swojej podsieci.
                byte[] info = (MAGIC + " " + PORT + " " + Build.MANUFACTURER + " " + Build.MODEL)
                        .getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n"
                        + "Content-Length: " + info.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(LATIN1));
                out.write(info);
                out.flush();
                return;
            }

            if (path.startsWith("/control")) {
                String body = handleControl(path);
                byte[] b = body.getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n"
                        + "Access-Control-Allow-Origin: *\r\n"
                        + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(LATIN1));
                out.write(b);
                out.flush();
                return;
            }

            if (!path.startsWith("/stream")) {
                byte[] html = ("<!doctype html><html><head><meta charset=utf-8>"
                        + "<meta name=viewport content='width=device-width'>"
                        + "<title>Ekran telefonu</title></head>"
                        + "<body style='margin:0;background:#000;display:flex;justify-content:center'>"
                        + "<img src='/stream' style='max-width:100vw;max-height:100vh'>"
                        + "</body></html>").getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                        + "Content-Length: " + html.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(LATIN1));
                out.write(html);
                out.flush();
                return;
            }

            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n"
                    + "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").getBytes(LATIN1));
            out.flush();
            requestFreshFrame();

            long seen = 0;
            while (!stopping) {
                byte[] f;
                synchronized (frameLock) {
                    while (frameId == seen && !stopping) frameLock.wait(1000);
                    if (stopping) break;
                    f = frame;
                    seen = frameId;
                }
                out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                        + f.length + "\r\n\r\n").getBytes(LATIN1));
                out.write(f);
                out.write("\r\n".getBytes(LATIN1));
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            clients.decrementAndGet();
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Wykonuje polecenie sterujace z komputera. Format to zapytanie GET, np.:
     *   /control?a=tap&amp;x=0.5&amp;y=0.3
     *   /control?a=swipe&amp;x=0.5&amp;y=0.8&amp;x2=0.5&amp;y2=0.2&amp;t=250
     *   /control?a=long&amp;x=0.5&amp;y=0.3
     *   /control?a=key&amp;k=back|home|recents
     *   /control?a=text&amp;s=&lt;tekst zakodowany w URL&gt;
     *   /control?a=del           (backspace)
     * Zwraca "OK", gdy usluga sterowania dziala, lub "NOACCESS", gdy jeszcze
     * nie zostala wlaczona w Ustawieniach -&gt; Ulatwienia dostepu.
     */
    private String handleControl(String path) {
        ControlService c = ControlService.instance;
        if (c == null) return "NOACCESS";
        int q = path.indexOf('?');
        if (q < 0) return "OK";
        java.util.Map<String, String> p = new java.util.HashMap<>();
        for (String pair : path.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) p.put(pair.substring(0, eq), urlDecode(pair.substring(eq + 1)));
        }
        String a = p.get("a");
        if (a == null) return "OK";
        try {
            switch (a) {
                case "tap":
                    c.tap(f(p, "x"), f(p, "y"));
                    break;
                case "long":
                    c.longPress(f(p, "x"), f(p, "y"));
                    break;
                case "swipe":
                    long t = p.containsKey("t") ? Long.parseLong(p.get("t")) : 200;
                    c.swipe(f(p, "x"), f(p, "y"), f(p, "x2"), f(p, "y2"), t);
                    break;
                case "key":
                    String k = p.get("k");
                    if ("back".equals(k)) c.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                    else if ("home".equals(k)) c.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
                    else if ("recents".equals(k)) c.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);
                    break;
                case "text":
                    String txt = p.get("s");
                    if (txt != null) c.type(txt);
                    break;
                case "del":
                    c.backspace();
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
        }
        return "OK";
    }

    private static float f(java.util.Map<String, String> p, String key) {
        String v = p.get(key);
        return v == null ? 0f : Float.parseFloat(v);
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** Nowy widz od razu dostaje aktualny obraz, nawet jeśli ekran się nie zmienia. */
    private void requestFreshFrame() {
        Handler h = captureHandler;
        if (h == null) return;
        h.post(new Runnable() {
            @Override
            public void run() {
                if (bitmap != null) {
                    dirty = true;
                    scheduleEncode();
                }
            }
        });
    }

    // ---------------------------------------------------------------- wykrywanie w sieci

    private void startBeacon() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    beacon = new DatagramSocket();
                    beacon.setBroadcast(true);
                    byte[] msg = (MAGIC + " " + PORT + " " + Build.MANUFACTURER + " " + Build.MODEL)
                            .getBytes("UTF-8");
                    while (!stopping) {
                        for (InetAddress addr : broadcastAddresses()) {
                            try {
                                beacon.send(new DatagramPacket(msg, msg.length, addr, DISCOVERY_PORT));
                            } catch (IOException ignored) {
                            }
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception ignored) {
                }
            }
        }, "beacon").start();
    }

    private static List<InetAddress> broadcastAddresses() {
        List<InetAddress> list = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null && !list.contains(b)) list.add(b);
                }
            }
            list.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * Adresy IPv4 telefonu w sieci lokalnej (do wyświetlenia użytkownikowi).
     * Pomija interfejsy sieci komórkowej, a Wi-Fi/hotspot/USB stawia na początku.
     */
    static List<String> localAddresses() {
        List<String> preferred = new ArrayList<>();
        List<String> other = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase();
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("v4-")
                        || name.startsWith("clat") || name.startsWith("dummy") || name.startsWith("r_rmnet")) {
                    continue;
                }
                boolean local = name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")
                        || name.startsWith("softap") || name.startsWith("rndis") || name.startsWith("eth")
                        || name.startsWith("ncm") || name.startsWith("bt-pan");
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        (local ? preferred : other).add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        preferred.addAll(other);
        return preferred;
    }

    // ---------------------------------------------------------------- sprzątanie

    @Override
    public void onDestroy() {
        running = false;
        stopping = true;
        synchronized (frameLock) {
            frameLock.notifyAll();
        }
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        if (beacon != null) beacon.close();
        if (displayManager != null) displayManager.unregisterDisplayListener(displayListener);
        if (display != null) display.release();
        if (projection != null) projection.stop();
        if (captureThread != null) {
            final ImageReader r = reader;
            captureHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (r != null) r.close();
                }
            });
            captureThread.quitSafely();
        }
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        super.onDestroy();
    }
}
