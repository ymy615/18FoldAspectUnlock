package android.content;

import android.os.Bundle;

public abstract class BroadcastReceiver {
    private Bundle extras;
    private int code;
    public abstract void onReceive(Context context, Intent intent);
    public void setResultExtras(Bundle extras) { this.extras = extras; }
    public void setResultCode(int code) { this.code = code; }
    public Bundle getResultExtras(boolean makeMap) {
        if (extras == null && makeMap) extras = new Bundle();
        return extras;
    }
    public int getResultCode() { return code; }
    public void resetResult() { extras = null; code = 0; }
}
