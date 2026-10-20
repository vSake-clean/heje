package pl.heje.ekran;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
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
