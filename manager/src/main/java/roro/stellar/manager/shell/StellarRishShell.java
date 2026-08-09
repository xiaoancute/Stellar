package roro.stellar.manager.shell;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;

import rikka.rish.Rish;
import rikka.rish.RishConfig;
import roro.stellar.shizuku.ShizukuCompat;

public final class StellarRishShell extends Rish {

    @Override
    public void requestPermission(Runnable onGrantedRunnable) {
        if (ShizukuCompat.INSTANCE.checkSelfPermission()
                == PackageManager.PERMISSION_GRANTED) {
            onGrantedRunnable.run();
            return;
        }

        if (ShizukuCompat.INSTANCE.shouldShowRequestPermissionRationale()) {
            abortPermissionDenied();
            return;
        }

        ShizukuCompat.OnRequestPermissionResultListener listener =
                new ShizukuCompat.OnRequestPermissionResultListener() {
                    @Override
                    public void onRequestPermissionResult(int requestCode, boolean allowed) {
                        ShizukuCompat.INSTANCE.removeRequestPermissionResultListener(this);
                        if (allowed) {
                            onGrantedRunnable.run();
                        } else {
                            abortPermissionDenied();
                        }
                    }
                };
        ShizukuCompat.INSTANCE.addRequestPermissionResultListener(listener);
        ShizukuCompat.INSTANCE.requestPermission(0);
    }

    public static void main(
            String[] args,
            String packageName,
            IBinder binder,
            Handler handler,
            String nativeLibraryPath
    ) {
        RishConfig.setLibraryPath(nativeLibraryPath);
        RishConfig.init(binder, "moe.shizuku.server.IShizukuService", 30000);
        ShizukuCompat.INSTANCE.onBinderReceived(binder, packageName);
        ShizukuCompat.INSTANCE.addBinderReceivedListener(() -> {
            int version = ShizukuCompat.INSTANCE.getVersion();
            if (version < 12) {
                System.err.println("stsh requires Stellar's Shizuku server version 12 or newer");
                System.err.flush();
                System.exit(1);
            }
            new StellarRishShell().start(args);
        });
    }

    private static void abortPermissionDenied() {
        System.err.println("Permission denied; allow Termux in Stellar");
        System.err.flush();
        System.exit(1);
    }
}
