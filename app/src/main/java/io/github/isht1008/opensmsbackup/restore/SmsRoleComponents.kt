package io.github.isht1008.opensmsbackup.restore

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder

/** Minimal Android SMS-role qualification components. Restore never processes incoming content. */
class RestoreSmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = Unit
}

class RestoreMmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = Unit
}

class RestoreRespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

class RestoreSendToActivity : Activity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        finish()
    }
}
