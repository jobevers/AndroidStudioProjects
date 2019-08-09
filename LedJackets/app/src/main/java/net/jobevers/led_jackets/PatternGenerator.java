package net.jobevers.led_jackets;

import android.app.IntentService;
import android.content.Intent;


public class PatternGenerator extends IntentService {
    /**
     * A constructor is required, and must call the super <code><a href="/reference/android/app/IntentService.html#IntentService(java.lang.String)">IntentService(String)</a></code>
     * constructor with a name for the worker thread.
     */
    public PatternGenerator() {
        super("PatternGeneratorService");
    }

    @Override
    protected void onHandleIntent(Intent workIntent) {
        int i=0;
    }
}
