package io.kayt.navshell

import android.annotation.SuppressLint
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.Navigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.get
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

typealias NavEnterTransition = @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
typealias NavExitTransition = @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
typealias NavSizeTransformTransition = @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform?

/**
 * ModalHost displays the screens on top of the content that is surrounded by a NavShell (e.g. an Scaffold)
 * a modal can be the start point of a nav graph, but whenever a NavController navigates to
 * a composable destination, all the modal destinations will be popped off the backstack
 */
@SuppressLint("StateFlowValueCalledInComposition", "RestrictedApi")
@Composable
fun ModalHost(
    navController: NavController,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentAlignment: Alignment = Alignment.TopStart,
    enterTransition: NavEnterTransition = { fadeIn(tween(700)) },
    exitTransition: NavExitTransition = { fadeOut(tween(700)) },
    popEnterTransition: NavEnterTransition = enterTransition,
    popExitTransition: NavExitTransition = exitTransition,
    sizeTransform: NavSizeTransformTransition? = null
) {
    // Find the ModalComposeNavigator, returning early if it isn't found
    val modalComposeNavigator =
        navController.navigatorProvider.get<Navigator<out NavDestination>>(ModalComposeNavigator.NAME)
                as? ModalComposeNavigator ?: return

    val currentBackStack by modalComposeNavigator.backStack.collectAsState()

    val currentEntryIsModal =
        navController.currentBackStackEntry?.destination?.navigatorName == ModalComposeNavigator.NAME

    var progress by remember { mutableFloatStateOf(0f) }
    var inPredictiveBack by remember { mutableStateOf(false) }

    val isOnlyOneEntry = navController.previousBackStackEntry == null
    PredictiveBackHandler(!isOnlyOneEntry && currentEntryIsModal) { backEvent ->
        progress = 0f
        val currentBackStackEntry = currentBackStack.lastOrNull()
        modalComposeNavigator.prepareForTransition(currentBackStackEntry!!)
        if (currentBackStack.size > 1) {
            val previousEntry = currentBackStack[currentBackStack.size - 2]
            modalComposeNavigator.prepareForTransition(previousEntry)
        }
        try {
            backEvent.collect {
                inPredictiveBack = true
                progress = it.progress
            }
            inPredictiveBack = false
            modalComposeNavigator.popBackStack(currentBackStackEntry, false)
        } catch (e: CancellationException) {
            inPredictiveBack = false
        }
    }

    val saveableStateHolder = rememberSaveableStateHolder()

    val allVisibleEntries by navController.visibleEntries.collectAsState()

    // Intercept back only when there's a destination to pop
    val visibleEntries by remember {
        derivedStateOf {
            allVisibleEntries.filter { entry ->
                entry.destination.navigatorName == ModalComposeNavigator.NAME
            }
        }
    }


    val backStackEntry: NavBackStackEntry? = visibleEntries.lastOrNull()

    val zIndices = remember { mutableMapOf<String, Float>() }

    if (backStackEntry != null) {
        val finalEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            val targetDestination = targetState.destination as ModalComposeNavigator.Destination

            if (modalComposeNavigator.isPop.value || inPredictiveBack) {
                targetDestination.hierarchy.firstNotNullOfOrNull { destination ->
                    destination.createPopEnterTransition(this)
                } ?: popEnterTransition.invoke(this)
            } else {
                targetDestination.hierarchy.firstNotNullOfOrNull { destination ->
                    destination.createEnterTransition(this)
                } ?: enterTransition.invoke(this)
            }
        }

        val finalExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            val initialDestination = initialState.destination as ModalComposeNavigator.Destination

            if (modalComposeNavigator.isPop.value || inPredictiveBack) {
                initialDestination.hierarchy.firstNotNullOfOrNull { destination ->
                    destination.createPopExitTransition(this)
                } ?: popExitTransition.invoke(this)
            } else {
                initialDestination.hierarchy.firstNotNullOfOrNull { destination ->
                    destination.createExitTransition(this)
                } ?: exitTransition.invoke(this)
            }
        }

        val finalSizeTransform:
                AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform? =
            {
                val targetDestination = targetState.destination as ModalComposeNavigator.Destination

                targetDestination.hierarchy.firstNotNullOfOrNull { destination ->
                    destination.createSizeTransform(this)
                } ?: sizeTransform?.invoke(this)
            }
        DisposableEffect(true) {
            onDispose {
                visibleEntries.forEach { entry -> modalComposeNavigator.onTransitionComplete(entry) }
            }
        }

        val transitionState = remember {
            // The state returned here cannot be nullable cause it produces the input of the
            // transitionSpec passed into the AnimatedContent and that must match the non-nullable
            // scope exposed by the transitions on the NavHost and composable APIs.
            SeekableTransitionState(backStackEntry)
        }

        val transition = rememberTransition(transitionState, label = "entry")

        // Animatable for the vertical position (in pixels)
        val alphaAnimation = remember { Animatable(0f) }

        if (inPredictiveBack) {
            LaunchedEffect(progress) {
                if (currentBackStack.size > 1) {
                    val previousEntry = currentBackStack[currentBackStack.size - 2]
                    transitionState.seekTo(progress, previousEntry)
                } else {
                    alphaAnimation.snapTo(1 - progress)
                }
            }
        } else {
            LaunchedEffect(backStackEntry) {
                // This ensures we don't animate after the back gesture is cancelled and we
                // are already on the current state
                if (transitionState.currentState != backStackEntry) {
                    transitionState.animateTo(backStackEntry)
                    alphaAnimation.animateTo(1f)
                } else {
                    // convert from nanoseconds to milliseconds
                    val totalDuration = transition.totalDurationNanos / 1000000
                    // When the predictive back gesture is cancel, we need to manually animate
                    // the SeekableTransitionState from where it left off, to zero and then
                    // snapTo the final position.
                    animate(
                        transitionState.fraction,
                        0f,
                        animationSpec = tween((transitionState.fraction * totalDuration).toInt())
                    ) { value, _ ->
                        this@LaunchedEffect.launch {
                            if (value > 0) {
                                // Seek the original transition back to the currentState
                                transitionState.seekTo(value)
                            }
                            if (value == 0f) {
                                // Once we animate to the start, we need to snap to the right state.
                                transitionState.snapTo(backStackEntry)
                            }
                        }
                    }
                }
            }
        }

        // Launch the animation when the Composable enters the composition
        LaunchedEffect(Unit) {
            // if it is the only entry in backstack (e.g. the app is started with a full screen), skip
            // the alpha animation
            if (isOnlyOneEntry) {
                alphaAnimation.snapTo(1f)
            }
            else {
                alphaAnimation.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(600)
                )
            }
        }

        LaunchedEffect(currentBackStack.size) {
            if (currentBackStack.isEmpty()) {
                alphaAnimation.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(600)
                )
                modalComposeNavigator.markAllAsComplete()
            }
        }

        transition.AnimatedContent(
            Modifier
                .alpha(alphaAnimation.value)
                .background(containerColor)
                .then(modifier),
            transitionSpec = {
                // If the initialState of the AnimatedContent is not in visibleEntries, we are in
                // a case where visible has cleared the old state for some reason, so instead of
                // attempting to animate away from the initialState, we skip the animation.
                if (initialState in visibleEntries) {
                    val initialZIndex =
                        zIndices[initialState.id] ?: 0f.also { zIndices[initialState.id] = 0f }
                    val targetZIndex =
                        when {
                            targetState.id == initialState.id -> initialZIndex
                            modalComposeNavigator.isPop.value || inPredictiveBack -> initialZIndex - 1f
                            else -> initialZIndex + 1f
                        }.also { zIndices[targetState.id] = it }

                    ContentTransform(
                        finalEnter(this),
                        finalExit(this),
                        targetZIndex,
                        finalSizeTransform(this)
                    )
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            contentAlignment,
            contentKey = { it.id }
        ) {
            // In some specific cases, such as clearing your back stack by changing your
            // start destination, AnimatedContent can contain an entry that is no longer
            // part of visible entries since it was cleared from the back stack and is not
            // animating. In these cases the currentEntry will be null, and in those cases,
            // AnimatedContent will just skip attempting to transition the old entry.
            // See https://issuetracker.google.com/238686802
            val currentEntry =
                if (inPredictiveBack) {
                    // We have to do this because the previous entry does not show up in
                    // visibleEntries
                    // even if we prepare it above as part of onBackStackChangeStarted
                    it
                } else {
                    visibleEntries.lastOrNull { entry -> it == entry }
                }

            // while in the scope of the composable, we provide the navBackStackEntry as the
            // ViewModelStoreOwner and LifecycleOwner
            currentEntry?.LocalOwnersProvider(saveableStateHolder) {
                (currentEntry.destination as ModalComposeNavigator.Destination).content(
                    this,
                    currentEntry
                )
            }
        }
        LaunchedEffect(transition.currentState, transition.targetState) {
            if (transition.currentState == transition.targetState) {
                visibleEntries.forEach { entry -> modalComposeNavigator.onTransitionComplete(entry) }
                zIndices
                    .filter { it.key != transition.targetState.id }
                    .forEach { zIndices.remove(it.key) }
            }
        }
    }
}


private fun NavDestination.createEnterTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
): EnterTransition? =
    when (this) {
        is ModalComposeNavigator.Destination -> this.enterTransition?.invoke(scope)
        else -> null
    }

private fun NavDestination.createExitTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
): ExitTransition? =
    when (this) {
        is ModalComposeNavigator.Destination -> this.exitTransition?.invoke(scope)
        else -> null
    }

private fun NavDestination.createPopEnterTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
): EnterTransition? =
    when (this) {
        is ModalComposeNavigator.Destination -> this.popEnterTransition?.invoke(scope)
        else -> null
    }

private fun NavDestination.createPopExitTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
): ExitTransition? =
    when (this) {
        is ModalComposeNavigator.Destination -> this.popExitTransition?.invoke(scope)
        else -> null
    }

private fun NavDestination.createSizeTransform(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>
): SizeTransform? =
    when (this) {
        is ModalComposeNavigator.Destination -> this.sizeTransform?.invoke(scope)
        else -> null
    }
