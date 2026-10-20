package pl.heje.ekran;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Ekran startowy: przycisk Start/Stop i informacja o adresie telefonu. */
public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1;
    private static final int REQ_NOTIFY = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button button;
    private Button controlButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(24, 26, 32));

        TextView title = new TextView(this);
        title.setText("Ekran telefonu na komputerze");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.rgb(200, 205, 215));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, pad, 0, pad);
        root.addView(status);

        button = new Button(this);
        button.setTextSize(18);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ScreenService.running) {
                    stopService(new Intent(MainActivity.this, ScreenService.class));
                    refreshLater();
                } else {
                    start();
                }
            }
        });
        root.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        controlButton = new Button(this);
        controlButton.setTextSize(16);
        controlButton.setText("Włącz sterowanie z komputera");
        controlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) {
                    status.setText("Otwórz Ustawienia → Ułatwienia dostępu i włącz "
                            + "„Ekran na PC - sterowanie”.");
                }
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = pad / 2;
        root.addView(controlButton, clp);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void start() {
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText("Nie udzielono zgody na przechwytywanie ekranu.");
            return;
        }
        Intent svc = new Intent(this, ScreenService.class)
                .putExtra(ScreenService.EXTRA_CODE, resultCode)
                .putExtra(ScreenService.EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        refreshLater();
    }

    private void refreshLater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 600);
    }

    private void refresh() {
        if (ScreenService.running) {
            StringBuilder sb = new StringBuilder();
            sb.append("Udostępnianie ekranu WŁĄCZONE.\n\n")
              .append("Uruchom EkranTelefonu.exe na komputerze podłączonym do tej samej sieci Wi-Fi ")
              .append("lub do hotspotu tego telefonu - ")
              .append("obraz pojawi się automatycznie.\n\n");
            List<String> ips = ScreenService.localAddresses();
            if (ips.isEmpty()) {
                sb.append("Brak połączenia z siecią!");
            } else {
                sb.append("Adres telefonu:\n");
                for (String ip : ips) sb.append(ip).append('\n');
                sb.append("\n(podgląd także w przeglądarce: http://")
                  .append(ips.get(0)).append(':').append(ScreenService.PORT).append(" )");
            }
            if (ControlService.instance != null) {
                sb.append("\n\nSterowanie z komputera: WŁĄCZONE.\n")
                  .append("Klikaj i przewijaj myszką w oknie na komputerze.");
            } else {
                sb.append("\n\nSterowanie z komputera jest wyłączone - podgląd działa, ")
                  .append("ale klikanie w oknie nic nie zrobi. Naciśnij przycisk poniżej, ")
                  .append("aby je włączyć.");
            }
            status.setText(sb.toString());
            button.setText("Zatrzymaj");
        } else {
            status.setText("Naciśnij Start i zezwól na nagrywanie ekranu.\n\n"
                    + "Aby sterować telefonem z komputera, włącz też „sterowanie” przyciskiem poniżej "
                    + "(Ustawienia → Ułatwienia dostępu).");
            button.setText("Start");
        }
        controlButton.setText(ControlService.instance != null
                ? "Sterowanie z komputera: włączone ✓"
                : "Włącz sterowanie z komputera");
    }
}
