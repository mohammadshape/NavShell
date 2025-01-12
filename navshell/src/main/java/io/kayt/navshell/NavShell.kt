package io.kayt.navshell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.Navigator
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.get
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun NavShell(
    navController: NavController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val modalNavigator = remember {
        ModalComposeNavigator()
    }
    navController.navigatorProvider.addNavigator(modalNavigator)

    Box(modifier) {
        // Emit the content first,
        // content should contain the NavHost that set the graph to NavController
        content()
        var showModalHost by remember { mutableStateOf(false) }

        // Add ModalHost to the composition after the first full composition
        // to let the NavHost to set the graph to the NavController
        LaunchedEffect(Unit) {
            // Check that the graph is set by calling NavHost in content()
            runCatching { navController.graph }.onFailure { error("NavHost must be initialized within a NavShell and configured with a valid navigation graph.") }
            showModalHost = true
        }
        if (showModalHost) {
            ModalHost(
                navController,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
}