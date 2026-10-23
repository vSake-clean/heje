package pl.odkrywca.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bufor punktów GPS zapisanych przez usługę w tle.
 * Usługa dopisuje punkty do pliku, a aplikacja (WebView) odbiera je metodą drain(),
 * dzięki czemu nic nie ginie, nawet gdy interfejs był zamknięty przez wiele godzin.
 */
final class PointStore {

    private static final String FILE = "pending-points.csv";
    private static final Object LOCK = new Object();

    private PointStore() {}

    static void append(Context ctx, long time, double lat, double lon, float acc, float bearing) {
        String line = String.format(Locale.US, "%d,%.7f,%.7f,%.1f,%.1f\n", time, lat, lon, acc, bearing);
        synchronized (LOCK) {
            try (FileWriter w = new FileWriter(new File(ctx.getFilesDir(), FILE), true)) {
                w.write(line);
            } catch (IOException ignored) {
                // brak miejsca na dysku – punkt pomijamy
            }
        }
    }

    /** Zwraca wszystkie zaległe punkty i czyści bufor. Każdy punkt: [czas, lat, lon, dokładność, kierunek]. */
    static List<double[]> drain(Context ctx) {
        List<double[]> out = new ArrayList<>();
        synchronized (LOCK) {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return out;
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split(",");
                    if (p.length < 5) continue;
                    try {
                        out.add(new double[] {
                            Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]), Double.parseDouble(p[4])
                        });
                    } catch (NumberFormatException ignored) {
                        // uszkodzona linia (np. przerwany zapis)
                    }
                }
            } catch (IOException ignored) {
                return out;
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        return out;
    }

    static int count(Context ctx) {
        synchronized (LOCK) {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return 0;
            int n = 0;
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                while (r.readLine() != null) n++;
            } catch (IOException ignored) {
                // nic
            }
            return n;
        }
    }
}
