package io.github.isht1008.opensmsbackup.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.isht1008.opensmsbackup.ui.screen.BackupHistoryScreen
import io.github.isht1008.opensmsbackup.ui.screen.HomeScreen
import io.github.isht1008.opensmsbackup.ui.screen.SettingsScreen
import io.github.isht1008.opensmsbackup.viewmodel.BackupHistoryViewModel
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel

@Composable
fun OpenSmsBackupNavHost(
    homeViewModel: HomeViewModel,
    onBackupClick: () -> Unit,
    onGoogleSignInClick: () -> Unit
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
                },

                onGoogleSignInClick = onGoogleSignInClick
            )
        }


        composable(Screen.BackupHistory.route) {

            val viewModel: BackupHistoryViewModel = viewModel()

            BackupHistoryScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
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