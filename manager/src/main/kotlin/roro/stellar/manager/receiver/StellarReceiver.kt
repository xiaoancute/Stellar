package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val data = intent.getBundleExtra(EXTRA_DATA) ?: return
        val callback = data.getBinder(EXTRA_CALLBACK) ?: return
        val binder = Stellar.binder
        if (binder == null || !binder.pingBinder()) {
            sendReply(callback, null, null)
            return
        }

        sendReply(callback, binder, context.applicationInfo.sourceDir)
    }

    private fun sendReply(callback: IBinder, binder: IBinder?, sourceDir: String?) {
        val data = android.os.Parcel.obtain()
        try {
            data.writeStrongBinder(binder)
            data.writeString(sourceDir)
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
