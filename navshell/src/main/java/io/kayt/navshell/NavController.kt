package io.kayt.navshell

import androidx.navigation.NavController

fun NavController.popAllModals() {
    while (currentBackStackEntry?.isModalComposeNavigator() == true) {
        popBackStack()
    }
}