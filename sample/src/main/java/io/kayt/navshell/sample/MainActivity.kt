package io.kayt.navshell.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import io.kayt.navshell.NavShell
import io.kayt.navshell.modal
import io.kayt.navshell.sample.ui.theme.NavShellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavShellTheme {
                MainUi()
            }
        }
    }
}

@Composable
fun MainUi() {
    val navController = rememberNavController()
    NavShell(navController) {
        Scaffold(
            bottomBar = {
                AppBottomBar()
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "ModalThird",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("First") {
                    SampleScreen(
                        containerColor = Color.Green,
                        screenTitle = "First screen"
                    ) {
                        navController.navigate("Second")
                    }
                }
                composable("Second") {
                    SampleScreen(
                        containerColor = Color.Red,
                        screenTitle = "Second screen"
                    ) {
                        navController.navigate("ModalThird")
                    }
                }
                modal("ModalThird") {
                    SampleScreen(
                        containerColor = Color.Black,
                        screenTitle = "MODAL"
                    ) {
                        navController.navigate("ModalForth")
                    }
                }
                modal("ModalForth") {
                    SampleScreen(
                        containerColor = Color.White,
                        screenTitle = "SECOND"
                    ) {
                        navController.navigate("First")
                    }
                }
            }
        }
    }
}

@Composable
fun SampleScreen(
    containerColor: Color,
    screenTitle: String,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Text(screenTitle, color = Color.Black)
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("Click on me")
            }
        }
    }
}

@Composable
fun AppBottomBar() {
    BottomAppBar(
        containerColor = Color.Transparent,
        actions = {
            IconButton(onClick = { /* do something */ }) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Localized description"
                )
            }
            IconButton(onClick = { /* do something */ }) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Localized description",
                )
            }
            IconButton(onClick = { /* do something */ }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Localized description",
                )
            }
            IconButton(onClick = { /* do something */ }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Localized description",
                )
            }
        })
}