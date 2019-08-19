package net.jobevers.led_jackets;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PatternService extends Service {
    public PatternService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
