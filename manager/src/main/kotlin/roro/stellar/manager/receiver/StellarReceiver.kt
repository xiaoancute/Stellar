package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import roro.stellar.Stellar

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
        val callback = intent.extras?.getBinder(EXTRA_CALLBACK)
            ?: intent.getBundleExtra(EXTRA_DATA)?.getBinder(EXTRA_CALLBACK)
            ?: return
        val binder = Stellar.binder
        if (binder != null && binder.pingBinder()) {
            sendReply(callback, binder)
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
                sendReply(callback, replyBinder)
            } finally {
                pending.finish()
            }
        }

        listener = Stellar.OnBinderReceivedListener {
            complete(Stellar.binder?.takeIf { it.pingBinder() })
        }
        Stellar.addBinderReceivedListener(listener, handler)
        handler.postDelayed({ complete(Stellar.binder?.takeIf { it.pingBinder() }) }, 8_000)
    }

    private fun sendReply(callback: IBinder, binder: IBinder?) {
        val data = android.os.Parcel.obtain()
        try {
            data.writeStrongBinder(binder)
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
    }
}
