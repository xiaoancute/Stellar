package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import roro.stellar.Stellar
import roro.stellar.StellarApiConstants

class StellarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REQUEST_SHELL_BINDER) {
            replyWithBinder(context, intent)
            return
        }

        if (Stellar.pingBinder()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                StellarReceiverStarter.start(context, forceStart = true)
            } finally {
                pending.finish()
            }
        }
    }

    private fun replyWithBinder(context: Context, intent: Intent) {
        val callback = intent.getBundleExtra(EXTRA_DATA)?.getBinder(EXTRA_CALLBACK)
        val binder = runCatching { requestShizukuBinder() }.getOrNull()
        if (binder != null) {
            setResultExtras(createResult(context, binder))
            sendReply(context, callback, binder)
            return
        }

        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        lateinit var listener: Stellar.OnBinderReceivedListener

        fun complete(replyBinder: IBinder?) {
            if (!completed.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            Stellar.removeBinderReceivedListener(listener)
            try {
                pending.setResultExtras(createResult(context, replyBinder))
                sendReply(context, callback, replyBinder)
            } finally {
                pending.finish()
            }
        }

        listener = Stellar.OnBinderReceivedListener {
            complete(runCatching { requestShizukuBinder() }.getOrNull())
        }
        Stellar.addBinderReceivedListener(listener, handler)
        handler.postDelayed({
            complete(runCatching { requestShizukuBinder() }.getOrNull())
        }, BINDER_WAIT_TIMEOUT_MILLIS)
    }

    private fun requestShizukuBinder(): IBinder? {
        val stellarBinder = Stellar.binder?.takeIf { it.pingBinder() } ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(StellarApiConstants.BINDER_DESCRIPTOR)
            if (!stellarBinder.transact(BINDER_TRANSACTION_GET_SHIZUKU_SERVICE, data, reply, 0)) {
                return null
            }
            reply.readException()
            reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun createResult(context: Context, binder: IBinder?): android.os.Bundle =
        android.os.Bundle().apply {
            putBinder(EXTRA_CALLBACK, binder)
            putString(EXTRA_SOURCE_DIR, context.applicationInfo.sourceDir)
        }

    private fun sendReply(context: Context, callback: IBinder?, binder: IBinder?) {
        if (callback == null) return
        val data = Parcel.obtain()
        try {
            data.writeStrongBinder(binder)
            data.writeString(context.applicationInfo.sourceDir)
            callback.transact(1, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    companion object {
        private const val ACTION_REQUEST_SHELL_BINDER =
            "roro.stellar.intent.action.REQUEST_SHELL_BINDER"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_CALLBACK = "binder"
        private const val EXTRA_SOURCE_DIR = "sourceDir"
        private const val BINDER_TRANSACTION_GET_SHIZUKU_SERVICE = 405
        private const val BINDER_WAIT_TIMEOUT_MILLIS = 8_000L
    }
}
