package io.github.isht1008.opensmsbackup.navigation

sealed class Screen(val route: String) {

    data object Home : Screen("home")

    data object BackupHistory : Screen("backup_history")
    data object BackupHealthDetail : Screen("backup_health_detail/{verificationId}") {
        fun route(id: Long) = "backup_health_detail/$id"
    }

    data object Settings : Screen("settings")
}
