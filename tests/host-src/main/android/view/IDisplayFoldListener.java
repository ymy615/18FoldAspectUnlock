package android.view;
public interface IDisplayFoldListener extends android.os.IInterface {
    android.os.IBinder asBinder();
    void onDisplayFoldChanged(int displayId, boolean folded);
}
