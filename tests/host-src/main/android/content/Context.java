package android.content;

import android.os.Handler;

/** Records registration metadata; does not claim to emulate Android permission enforcement. */
public final class Context {
    public static final int RECEIVER_EXPORTED = 2;
    private final ContentResolver resolver;
    public BroadcastReceiver receiver;
    public IntentFilter filter;
    public String receiverPermission;
    public Handler receiverHandler;
    public int receiverFlags;
    public int registrations;
    public boolean failRegistration;
    public Context() { this(new ContentResolver()); }
    public Context(ContentResolver resolver) { this.resolver = resolver; }
    public ContentResolver getContentResolver() { return resolver; }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String permission, Handler handler, int flags) {
        if (failRegistration) throw new IllegalStateException("injected registration failure");
        this.receiver = receiver; this.filter = filter; receiverPermission = permission;
        receiverHandler = handler; receiverFlags = flags; registrations++;
        return null;
    }
    public BroadcastReceiver dispatchOrdered(Intent intent) {
        if (receiver == null) throw new AssertionError("Receiver was not registered");
        if (!"android".equals(intent.getPackage())) throw new AssertionError("Test requests must target android explicitly");
        receiver.resetResult();
        receiver.onReceive(this, intent);
        return receiver;
    }
}
