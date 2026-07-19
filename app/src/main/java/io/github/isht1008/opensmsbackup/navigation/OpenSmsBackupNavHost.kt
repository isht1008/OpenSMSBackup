package io.github.isht1008.opensmsbackup.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.isht1008.opensmsbackup.ui.screen.BackupHealthScreen
import io.github.isht1008.opensmsbackup.ui.screen.BackupHealthDetailScreen
import io.github.isht1008.opensmsbackup.ui.screen.HomeScreen
import io.github.isht1008.opensmsbackup.ui.screen.SettingsScreen
import io.github.isht1008.opensmsbackup.viewmodel.BackupHealthViewModel
import io.github.isht1008.opensmsbackup.viewmodel.BackupHealthDetailViewModel
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel

@Composable
fun OpenSmsBackupNavHost(
    homeViewModel: HomeViewModel,
    onBackupClick: () -> Unit
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {

        composable(Screen.Home.route) {

            HomeScreen(
                viewModel = homeViewModel,

                onBackupClick = onBackupClick,

                onBackupHistoryClick = {
                    navController.navigate(
                        Screen.BackupHistory.route
                    )
                },

                onSettingsClick = {
                    navController.navigate(
                        Screen.Settings.route
                    )
                }
            )
        }


        composable(Screen.BackupHistory.route) {

            val viewModel: BackupHealthViewModel = viewModel()

            BackupHealthScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onCreateBackup = { navController.popBackStack() },
                onOpenDetails = { navController.navigate(Screen.BackupHealthDetail.route(it)) }
            )
        }

        composable(Screen.BackupHealthDetail.route) {
            val viewModel: BackupHealthDetailViewModel = viewModel()
            BackupHealthDetailScreen(viewModel, onBackClick = { navController.popBackStack() })
        }


        composable(Screen.Settings.route) {

            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

    }

}
