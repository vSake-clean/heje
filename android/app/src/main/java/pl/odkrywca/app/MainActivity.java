package pl.odkrywca.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(OdkrywcaPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // zapis działa zawsze; po każdym otwarciu upewniamy się, że usługa żyje
        TrackingService.ensureRunning(this);
    }
}
