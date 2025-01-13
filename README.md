## NavShell

No need for inner NavHost and coordinating between them anymore, just wrap your root scaffold with `NavShell` and use `modal` to have full-screen composable. it also supports PredictiveBackGusture (it animate alpha for now).

## Navigating to/from a Modal
Defining a modal is exactly the same as defining a composable, and it is true for navigating to them.

just consider that modals are kind of FloatingWindow so navigating from a Modal to a Composable cause all modal popped.

You can start your NavHost with a Modal as well.

## Sample
```kotlin
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
                startDestination = "First",
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
```

### Screenshot
[nav_shell.webm](https://github.com/user-attachments/assets/99381c22-8f0e-4c11-b65c-1d417f62e8e0)
