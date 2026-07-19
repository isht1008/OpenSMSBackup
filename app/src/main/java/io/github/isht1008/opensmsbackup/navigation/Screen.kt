package io.github.isht1008.opensmsbackup.navigation

sealed class Screen(val route: String) {

    data object Home : Screen("home")

    data object BackupHistory : Screen("backup_history")

    data object Settings : Screen("settings")
}
