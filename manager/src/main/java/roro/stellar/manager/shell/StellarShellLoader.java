package roro.stellar.manager.shell;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.IIntentReceiver;
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

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.BaseDexClassLoader;
import rikka.hidden.compat.PackageManagerApis;
import stub.dalvik.system.VMRuntimeHidden;

public final class StellarShellLoader {

    private static final String ACTION_REQUEST_BINDER =
            "roro.stellar.intent.action.REQUEST_SHELL_BINDER";
    private static final long REQUEST_TIMEOUT_MILLIS = 15000;

    private static String[] args;
    private static String callingPackage;
    private static Handler handler;
    private static final AtomicBoolean binderReceived = new AtomicBoolean(false);

    private static final Binder receiverBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != 1) {
                return super.onTransact(code, data, reply, flags);
            }

            IBinder binder = data.readStrongBinder();
            String sourceDir = data.readString();
            dispatchBinderResult(binder, sourceDir);
            return true;
        }
    };

    private static final IIntentReceiver resultReceiver = new IIntentReceiver.Stub() {
        @Override
        public void performReceive(
                Intent intent,
                int resultCode,
                String resultData,
                Bundle resultExtras,
                boolean ordered,
                boolean sticky,
                int sendingUser
        ) {
            IBinder binder = resultExtras == null
                    ? null
                    : resultExtras.getBinder("binder");
            String sourceDir = resultExtras == null
                    ? null
                    : resultExtras.getString("sourceDir");
            dispatchBinderResult(binder, sourceDir);
        }
    };

    private static void dispatchBinderResult(IBinder binder, String sourceDir) {
        if (!binderReceived.compareAndSet(false, true)) {
            return;
        }
        if (binder == null || TextUtils.isEmpty(sourceDir)) {
            abort("Stellar service is not running");
            return;
        }

        handler.removeCallbacksAndMessages(null);
        handler.post(() -> onBinderReceived(binder, sourceDir));
    }

    private static void requestForBinder() throws RemoteException {
        Bundle data = new Bundle();
        data.putBinder("binder", receiverBinder);

        String managerPackage = System.getenv("STSH_MANAGER_APPLICATION_ID");
        if (TextUtils.isEmpty(managerPackage)) {
            managerPackage = "roro.stellar.manager";
        }

        Intent intent = new Intent(ACTION_REQUEST_BINDER)
                .setPackage(managerPackage)
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
            activityManager.broadcastIntent(null, intent, null, resultReceiver, 0, null, null,
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
        File apk = new File(sourceDir);
        File parent = apk.getParentFile();
        if (parent == null) {
            abort("Invalid Stellar APK path");
            return;
        }

        String nativeLibraryPath = parent.getPath() + "/lib/"
                + VMRuntimeHidden.getRuntime().vmInstructionSet();
        String librarySearchPath = nativeLibraryPath;
        String systemLibrarySearchPath = System.getProperty("java.library.path");
        if (!TextUtils.isEmpty(systemLibrarySearchPath)) {
            librarySearchPath += File.pathSeparatorChar + systemLibrarySearchPath;
        }

        try {
            ClassLoader loaderClassLoader = StellarShellLoader.class.getClassLoader();
            ClassLoader shellParent = loaderClassLoader == null
                    ? null
                    : loaderClassLoader.getParent();
            BaseDexClassLoader classLoader = new BaseDexClassLoader(
                    sourceDir,
                    null,
                    librarySearchPath,
                    shellParent
            );
            Class<?> shellClass = classLoader.loadClass(
                    "roro.stellar.manager.shell.StellarRishShell"
            );
            shellClass.getDeclaredMethod(
                    "main",
                    String[].class,
                    String.class,
                    IBinder.class,
                    Handler.class,
                    String.class
            ).invoke(null, args, callingPackage, binder, handler, nativeLibraryPath);
        } catch (ClassNotFoundException error) {
            abort("Stellar shell classes are missing from the installed APK");
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

        handler.postDelayed(
                () -> abort("Request timed out for " + callingPackage),
                REQUEST_TIMEOUT_MILLIS
        );
        Looper.loop();
    }

    private static void abort(String message) {
        System.err.println(message);
        System.err.flush();
        System.exit(1);
    }
}
