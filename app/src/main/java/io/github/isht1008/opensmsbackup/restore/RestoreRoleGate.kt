package io.github.isht1008.opensmsbackup.restore

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

fun interface RestoreRoleGate { fun isGranted(): Boolean }

class RoleGatedRestoreExecutor(
    private val gate: RestoreRoleGate,
    private val engine: RestoreEngine
) {
    suspend fun execute(
        messages: List<io.github.isht1008.opensmsbackup.sms.SmsMessage>,
        existing: List<io.github.isht1008.opensmsbackup.sms.SmsMessage>,
        region: String
    ): RestoreResult? = if (gate.isGranted()) engine.restore(messages, existing, region) else null
}

class AndroidRestoreRoleGate(private val context: Context) : RestoreRoleGate {
    private val manager = context.getSystemService(RoleManager::class.java)
    override fun isGranted(): Boolean = manager.isRoleHeld(RoleManager.ROLE_SMS)
    fun requestIntent(): Intent = manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
}
