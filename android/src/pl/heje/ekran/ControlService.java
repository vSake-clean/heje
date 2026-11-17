package pl.heje.ekran;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Usluga ulatwien dostepu, ktora wykonuje na telefonie polecenia przychodzace
 * z komputera: dotkniecia, przeciagniecia, przewijanie, przyciski systemowe
 * (wstecz / ekran glowny / ostatnie) oraz wpisywanie tekstu.
 *
 * Wspolrzedne przychodza jako liczby wzgledne 0..1 (ulamek szerokosci / wysokosci
 * ekranu), dzieki czemu dzialaja niezaleznie od rozdzielczosci podgladu na PC.
 *
 * Uzytkownik musi te usluge raz wlaczyc w Ustawieniach -> Ulatwienia dostepu.
 */
public class ControlService extends AccessibilityService {
    static volatile ControlService instance;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    private int screenW() {
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        return m.widthPixels;
    }

    private int screenH() {
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        return m.heightPixels;
    }

    /** Krotkie dotkniecie w punkcie (rx, ry) podanym jako ulamek 0..1. */
    void tap(final float rx, final float ry) {
        swipe(rx, ry, rx, ry, 1);
    }

    /** Dlugie przytrzymanie (np. menu kontekstowe). */
    void longPress(final float rx, final float ry) {
        swipe(rx, ry, rx, ry, 600);
    }

    /** Przeciagniecie / przewiniecie od punktu do punktu w zadanym czasie (ms). */
    void swipe(final float rx1, final float ry1, final float rx2, final float ry2, final long durationMs) {
        main.post(new Runnable() {
            @Override
            public void run() {
                int w = screenW(), h = screenH();
                float x1 = clamp(rx1) * w, y1 = clamp(ry1) * h;
                float x2 = clamp(rx2) * w, y2 = clamp(ry2) * h;
                Path path = new Path();
                path.moveTo(x1, y1);
                if (x1 != x2 || y1 != y2) {
                    path.lineTo(x2, y2);
                }
                long dur = Math.max(1, durationMs);
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(path, 0, dur);
                GestureDescription g = new GestureDescription.Builder().addStroke(stroke).build();
                dispatchGesture(g, null, null);
            }
        });
    }

    // ------------------------------------------------------------ gest na żywo
    // Palec "dotyka" ekranu od wciśnięcia przycisku myszy do jego puszczenia,
    // a ruchy są wysyłane na bieżąco (Android 8+: StrokeDescription.continueStroke).
    // Kolejny odcinek wysyłamy dopiero, gdy poprzedni się zakończy, a w międzyczasie
    // zapamiętujemy tylko najnowszy punkt – dzięki temu gest nie "zostaje w tyle".

    private GestureDescription.StrokeDescription liveStroke;
    private boolean liveInFlight;
    private float liveX, liveY;          // koniec ostatnio wysłanego odcinka (piksele)
    private long liveTime;               // kiedy go wysłano
    private boolean pendMove, pendUp;
    private float pendX, pendY;
    private int liveGen;                 // numer bieżącego gestu – stare wywołania zwrotne są ignorowane
    // Android 7.x nie ma continueStroke – tam zbieramy punkty i wysyłamy całość przy puszczeniu.
    private Path fallbackPath;
    private long fallbackStart;

    void down(final float rx, final float ry) {
        main.post(new Runnable() {
            @Override
            public void run() {
                float x = clamp(rx) * screenW(), y = clamp(ry) * screenH();
                pendMove = pendUp = false;
                if (Build.VERSION.SDK_INT < 26) {
                    fallbackPath = new Path();
                    fallbackPath.moveTo(x, y);
                    fallbackStart = SystemClock.uptimeMillis();
                    return;
                }
                Path p = new Path();
                p.moveTo(x, y);
                liveGen++;
                liveInFlight = false;
                liveStroke = new GestureDescription.StrokeDescription(p, 0, 1, true);
                liveX = x;
                liveY = y;
                dispatchLive(liveStroke);
            }
        });
    }

    void move(final float rx, final float ry) {
        main.post(new Runnable() {
            @Override
            public void run() {
                float x = clamp(rx) * screenW(), y = clamp(ry) * screenH();
                if (Build.VERSION.SDK_INT < 26) {
                    if (fallbackPath != null) fallbackPath.lineTo(x, y);
                    return;
                }
                if (liveStroke == null) return;
                pendX = x;
                pendY = y;
                pendMove = true;
                if (!liveInFlight) dispatchNextLive();
            }
        });
    }

    void up(final float rx, final float ry) {
        main.post(new Runnable() {
            @Override
            public void run() {
                float x = clamp(rx) * screenW(), y = clamp(ry) * screenH();
                if (Build.VERSION.SDK_INT < 26) {
                    if (fallbackPath == null) return;
                    fallbackPath.lineTo(x, y);
                    long dur = Math.max(1, Math.min(SystemClock.uptimeMillis() - fallbackStart, 10000));
                    dispatchGesture(new GestureDescription.Builder().addStroke(
                            new GestureDescription.StrokeDescription(fallbackPath, 0, dur)).build(), null, null);
                    fallbackPath = null;
                    return;
                }
                if (liveStroke == null) return;
                pendX = x;
                pendY = y;
                pendUp = true;
                if (!liveInFlight) dispatchNextLive();
            }
        });
    }

    private void dispatchNextLive() {
        if (liveStroke == null || (!pendMove && !pendUp)) return;
        boolean last = pendUp;
        Path p = new Path();
        p.moveTo(liveX, liveY);
        if (pendX != liveX || pendY != liveY) p.lineTo(pendX, pendY);
        long now = SystemClock.uptimeMillis();
        long dur = Math.max(5, Math.min(now - liveTime, 100));
        try {
            liveStroke = liveStroke.continueStroke(p, 0, dur, !last);
        } catch (Exception e) {
            liveStroke = null;
            return;
        }
        liveX = pendX;
        liveY = pendY;
        pendMove = pendUp = false;
        GestureDescription.StrokeDescription s = liveStroke;
        if (last) liveStroke = null;
        dispatchLive(s);
    }

    private void dispatchLive(GestureDescription.StrokeDescription s) {
        liveInFlight = true;
        liveTime = SystemClock.uptimeMillis();
        final int gen = liveGen;
        boolean ok = dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription g) {
                        if (gen != liveGen) return;
                        liveInFlight = false;
                        dispatchNextLive();
                    }

                    @Override
                    public void onCancelled(GestureDescription g) {
                        if (gen != liveGen) return;
                        liveInFlight = false;
                        liveStroke = null;
                    }
                }, main);
        if (!ok) {
            liveInFlight = false;
            liveStroke = null;
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Przycisk systemowy wg stalych GLOBAL_ACTION_*. */
    void global(final int action) {
        main.post(new Runnable() {
            @Override
            public void run() {
                performGlobalAction(action);
            }
        });
    }

    /** Wpisuje tekst do aktualnie zaznaczonego pola. */
    void type(final String text) {
        main.post(new Runnable() {
            @Override
            public void run() {
                AccessibilityNodeInfo node = findFocusedEditable();
                if (node == null) return;
                CharSequence existing = node.getText();
                String combined = (existing == null ? "" : existing.toString()) + text;
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined);
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                node.recycle();
            }
        });
    }

    /** Backspace: usuwa ostatni znak w zaznaczonym polu. */
    void backspace() {
        main.post(new Runnable() {
            @Override
            public void run() {
                AccessibilityNodeInfo node = findFocusedEditable();
                if (node == null) return;
                CharSequence existing = node.getText();
                if (existing != null && existing.length() > 0) {
                    String cut = existing.subSequence(0, existing.length() - 1).toString();
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cut);
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
                node.recycle();
            }
        });
    }

    private AccessibilityNodeInfo findFocusedEditable() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        root.recycle();
        if (focus != null && focus.isEditable()) {
            return focus;
        }
        if (focus != null) {
            focus.recycle();
        }
        return null;
    }
}
