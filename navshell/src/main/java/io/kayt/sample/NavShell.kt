package io.kayt.sample

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
//            .apply {
//                onNavigateCallback = {
//                    val currentEntry = navController.currentBackStackEntry
//                    if (currentEntry?.isComposeNavigator() == true) {
//                        navController.navigatorProvider[ComposeNavigator::class]
//                            .prepareForTransition(navController.currentBackStackEntry!!)
//                    }
//                }
//            }
    }
    navController.navigatorProvider.addNavigator(modalNavigator)

    Box(modifier) {
        // Emit the content first,
        // content should contain the NavHost that set the graph to NavController
        content()
        var showModalHost by remember { mutableStateOf(false) }

        // Add ModalHost to the composition after the first full composition
        // to let the NavHost to set the graph to NavController
        LaunchedEffect(Unit) {
            // Check that the graph is set by calling NavHost in content()
            runCatching { navController.graph }.onFailure { error("NavHost must be initialized within a NavShell and configured with a valid navigation graph.") }
            showModalHost = true
        }
        if (showModalHost) {
            val state = navController.navigatorProvider[ModalComposeNavigator::class].s
            println("mmd state : $state")
            ModalHost(
                navController,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            // This should handle the backstack of both ModalHost and NavHost
            BackStackBackHandler(navController)
            ErrorWhenComposablePushOnTopOfModal(navController)
//            KeepFullScreenBehind(navController)
        }
    }
}

//@Composable
//private fun KeepFullScreenBehind(navController: NavController) {
//    val currentEntry by navController.currentBackStackEntryAsState()
//    if (currentEntry?.isModalComposeNavigator() == true) {
//        val previousIsFull = navController.previousBackStackEntry?.isComposeNavigator() == true
//        DisposableEffect(previousIsFull) {
//            if (previousIsFull) {
//                navController.navigatorProvider[ComposeNavigator::class]
//                    .prepareForTransition(navController.previousBackStackEntry!!)
//                navController.navigatorProvider[ComposeNavigator::class]
//                    .prepareForTransition(navController.previousBackStackEntry!!)
//                navController.navigatorProvider[ComposeNavigator::class]
//                    .prepareForTransition(navController.previousBackStackEntry!!)
//                navController.navigatorProvider[ComposeNavigator::class]
//                    .prepareForTransition(navController.previousBackStackEntry!!)
//            }
//            onDispose {
//
//            }
//        }
//    }
//}

@Composable
private fun BackStackBackHandler(navController: NavController) {
    val currentEntry by navController.currentBackStackEntryAsState()

    val composeNavigator = navController.navigatorProvider[ComposeNavigator::class]
    val modalComposeNavigator = navController.navigatorProvider[ModalComposeNavigator::class]
    val composeBackStackCount = composeNavigator.backStack.collectAsState().value.size
    val modalComposeBackStackCount = modalComposeNavigator.backStack.collectAsState().value.size
    val backStackCount = composeBackStackCount + modalComposeBackStackCount
    var progress by remember { mutableFloatStateOf(0f) }
    var inPredictiveBack by remember { mutableStateOf(false) }

    val previousEntry = navController.previousBackStackEntry
    PredictiveBackHandler(backStackCount > 1 && previousEntry!!.destination.navigatorName != currentEntry!!.destination.navigatorName) { backEvent ->
        progress = 0f
        val currentBackStackEntry = currentEntry!!
        currentBackStackEntry.getTransitionBaseNavigator(navController)
            .prepareForTransition(currentBackStackEntry)
        // Previous screen should exist as the PredictiveBack is just called if there is at least two entries in the stack
        val previousEntry = previousEntry!!
        previousEntry.getTransitionBaseNavigator(navController)
            .prepareForTransition(previousEntry)
        try {
            backEvent.collect {
                inPredictiveBack = true
                progress = it.progress
            }
            inPredictiveBack = false
            currentBackStackEntry.getTransitionBaseNavigator(navController)
                .getNavigator()
                .popBackStack(currentBackStackEntry, false)

            // Here we should check that if there is just one modal in the backstack we should
            // complete the transaction manually, otherwise it will stay on transitions list forever
            // TODO: It cause a visual lag when user tries to press back button frequently
            //  Maybe some changes should be done inside the ModalHost to handle the complete transition
            //  when the latest screen popped
            currentBackStackEntry.isModalComposeNavigator() && modalComposeBackStackCount == 1 &&
                    currentBackStackEntry.getTransitionBaseNavigator(navController)
                        .onTransitionComplete(currentBackStackEntry).let { true }

        } catch (e: CancellationException) {
            inPredictiveBack = false
        }
    }
}


@Composable
private fun ErrorWhenComposablePushOnTopOfModal(navController: NavController) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val previousEntry = navController.previousBackStackEntry
    if (currentEntry != null && previousEntry != null) {
        if (previousEntry.isModalComposeNavigator() && currentEntry!!.isComposeNavigator()) {
            error("A composable screen can not push on top of a modal, please pop all the modal first then push the composable")
        }
    }
}

private fun NavBackStackEntry.getTransitionBaseNavigator(navController: NavController) = when {
    isComposeNavigator() -> ComposeNavigatorTransitionBaseNavigator(navController.navigatorProvider[ComposeNavigator::class])
    isModalComposeNavigator() -> ModalComposeNavigatorTransitionBaseNavigator(navController.navigatorProvider[ModalComposeNavigator::class])
    else -> error("There is no any Transition base implementation for ${destination.navigatorName} navigator.")
}

internal fun NavBackStackEntry.isComposeNavigator() =
    // the name comes from ComposeNavigator.NAME but it is internal
    this.destination.navigatorName == "composable"

internal fun NavBackStackEntry.isModalComposeNavigator() =
    this.destination.navigatorName == ModalComposeNavigator.NAME

private interface TransitionBaseNavigator {
    fun prepareForTransition(entry: NavBackStackEntry)
    fun onTransitionComplete(entry: NavBackStackEntry)
    fun getNavigator(): Navigator<*>
}

private class ComposeNavigatorTransitionBaseNavigator(val composeNavigator: ComposeNavigator) :
    TransitionBaseNavigator {
    override fun prepareForTransition(entry: NavBackStackEntry) {
        composeNavigator.prepareForTransition(entry)
    }

    override fun onTransitionComplete(entry: NavBackStackEntry) {
        composeNavigator.onTransitionComplete(entry)
    }

    override fun getNavigator(): Navigator<ComposeNavigator.Destination> {
        return composeNavigator
    }

}

private class ModalComposeNavigatorTransitionBaseNavigator(val modalComposeNavigator: ModalComposeNavigator) :
    TransitionBaseNavigator {
    override fun prepareForTransition(entry: NavBackStackEntry) {
        modalComposeNavigator.prepareForTransition(entry)
    }

    override fun onTransitionComplete(entry: NavBackStackEntry) {
        modalComposeNavigator.onTransitionComplete(entry)
    }

    override fun getNavigator(): Navigator<*> {
        return modalComposeNavigator
    }
}