package io.github.isht1008.opensmsbackup

import android.app.Application
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorPreviewDiagnostics
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorPreviewStartupReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenSmsBackupApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FullMirrorPreviewDiagnostics.processReconstruction()
        applicationScope.launch {
            try {
                FullMirrorPreviewStartupReconciler(this@OpenSmsBackupApplication).reconcile()
            } catch (_: Throwable) {
                FullMirrorPreviewDiagnostics.startupFailure()
            }
        }
    }
}
