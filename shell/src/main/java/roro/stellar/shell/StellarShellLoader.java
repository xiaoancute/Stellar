package roro.stellar.shell;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.Objects;

import dalvik.system.BaseDexClassLoader;
import rikka.hidden.compat.PackageManagerApis;

public final class StellarShellLoader {

    private static final String ACTION_REQUEST_BINDER =
            "roro.stellar.intent.action.REQUEST_SHELL_BINDER";
    private static final String MANAGER_PACKAGE = "roro.stellar.manager";
    private static final String SHELL_CLASS = "roro.stellar.manager.shell.StellarShell";

    private static String[] args;
    private static String callingPackage;
    private static Handler handler;

    private static final Binder receiverBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == 1) {
                IBinder binder = data.readStrongBinder();
                String sourceDir = data.readString();
                if (binder != null && sourceDir != null) {
                    handler.post(() -> onBinderReceived(binder, sourceDir));
                } else {
                    abort("Stellar service is not running");
                }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private static void requestForBinder() throws RemoteException {
        Bundle data = new Bundle();
        data.putBinder("binder", receiverBinder);

        Intent intent = new Intent(ACTION_REQUEST_BINDER)
                .setPackage(MANAGER_PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("data", data);

        IBinder activityBinder = ServiceManager.getService("activity");
        IActivityManager activityManager;
        if (Build.VERSION.SDK_INT >= 26) {
            activityManager = IActivityManager.Stub.asInterface(activityBinder);
        } else {
            activityManager = ActivityManagerNative.asInterface(activityBinder);
        }

        try {
            activityManager.broadcastIntent(null, intent, null, null, 0, null, null,
                    null, -1, null, true, false, 0);
        } catch (Throwable error) {
            if ((Build.VERSION.SDK_INT != Build.VERSION_CODES.O
                    && Build.VERSION.SDK_INT != Build.VERSION_CODES.O_MR1)
                    || !Objects.equals(error.getMessage(),
                    "Calling application did not provide package name")) {
                throw error;
            }

            Intent activityIntent = Intent.createChooser(
                    new Intent(ACTION_REQUEST_BINDER)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            .putExtra("data", data),
                    "Request binder from Stellar"
            );
            activityManager.startActivityAsUser(null, callingPackage, activityIntent,
                    null, null, null, 0, 0, null, null, Os.getuid() / 100000);
        }
    }

    private static void onBinderReceived(IBinder binder, String sourceDir) {
        try {
            handler.removeCallbacksAndMessages(null);
            BaseDexClassLoader classLoader = new BaseDexClassLoader(
                    sourceDir, null, System.getProperty("java.library.path"),
                    ClassLoader.getSystemClassLoader());
            Class<?> shell = classLoader.loadClass(SHELL_CLASS);
            Method main = shell.getDeclaredMethod("main", String[].class, String.class,
                    IBinder.class, Handler.class);
            main.invoke(null, args, callingPackage, binder, handler);
        } catch (ClassNotFoundException error) {
            abort("Stellar manager does not contain the stsh shell client");
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
    }

    public static void main(String[] arguments) {
        args = arguments;

        String packageName = null;
        try {
            var packages = PackageManagerApis.getPackagesForUidNoThrow(Os.getuid());
            if (packages.size() == 1) {
                packageName = packages.get(0);
            }
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(packageName)) {
            packageName = System.getenv("STSH_APPLICATION_ID");
        }
        if (TextUtils.isEmpty(packageName) || "PKG".equals(packageName)) {
            abort("STSH_APPLICATION_ID is not set");
            return;
        }
        callingPackage = packageName;

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
        handler = new Handler(Looper.getMainLooper());

        try {
            requestForBinder();
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }

        final String packageForMessage = packageName;
        handler.postDelayed(() -> abort("Request timed out for " + packageForMessage), 10000);
        Looper.loop();
    }

    private static void abort(String message) {
        System.err.println(message);
        System.err.flush();
        System.exit(1);
    }
}
