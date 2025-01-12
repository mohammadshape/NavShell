package io.kayt.navshell

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.compose.ComposeNavigator

@Navigator.Name("modal_composable")
class ModalComposeNavigator : Navigator<ModalComposeNavigator.Destination>() {

    /** Get the map of transitions currently in progress from the [state]. */
    internal val transitionsInProgress
        get() = state.transitionsInProgress

    /** Get the back stack from the [state]. */
    public val backStack
        get() = state.backStack

    internal val isPop = mutableStateOf(false)
    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        entries.forEach { entry -> state.pushWithTransition(entry) }
        isPop.value = false
    }

    override fun createDestination(): Destination {
        return Destination(this) {}
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
        isPop.value = true
        if (state.backStack.value.isEmpty()) {
            state.transitionsInProgress.value.forEachIndexed { index, entry ->
                onTransitionComplete(entry)
            }
        }
    }

    /**
     * Function to prepare the entry for transition.
     *
     * This should be called when the entry needs to move the [Lifecycle.State] in preparation for a
     * transition such as when using predictive back.
     */
    public fun prepareForTransition(entry: NavBackStackEntry) {
        state.prepareForTransition(entry)
    }

    /**
     * Callback to mark a navigation in transition as complete.
     *
     * This should be called in conjunction with [navigate] and [popBackStack] as those calls merely
     * start a transition to the target destination, and requires manually marking the transition as
     * complete by calling this method.
     *
     * Failing to call this method could result in entries being prevented from reaching their final
     * [Lifecycle.State].
     */
    public fun onTransitionComplete(entry: NavBackStackEntry) {
        state.markTransitionComplete(entry)
    }

    /** NavDestination specific to [ComposeNavigator] */
    @NavDestination.ClassType(Composable::class)
    public class Destination(
        navigator: ModalComposeNavigator,
        internal val content:
        @Composable
        AnimatedContentScope.(@JvmSuppressWildcards NavBackStackEntry) -> Unit
    ) : NavDestination(navigator), FloatingWindow {

        @Deprecated(
            message = "Deprecated in favor of Destination that supports AnimatedContent",
            level = DeprecationLevel.HIDDEN,
        )
        constructor(
            navigator: ModalComposeNavigator,
            content: @Composable (NavBackStackEntry) -> @JvmSuppressWildcards Unit
        ) : this(navigator, content = { entry -> content(entry) })

        internal var enterTransition:
                (@JvmSuppressWildcards
                AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? =
            null

        internal var exitTransition:
                (@JvmSuppressWildcards
                AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? =
            null

        internal var popEnterTransition:
                (@JvmSuppressWildcards
                AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? =
            null

        internal var popExitTransition:
                (@JvmSuppressWildcards
                AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? =
            null

        internal var sizeTransform:
                (@JvmSuppressWildcards
                AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform?)? =
            null
    }

    internal companion object {
        internal const val NAME = "modal_composable"
    }
}
