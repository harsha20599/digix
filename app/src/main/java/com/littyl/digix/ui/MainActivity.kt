package com.littyl.digix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.Store
import com.littyl.digix.engine.GoogleHome
import com.littyl.digix.ui.onboarding.OnboardingScreen
import com.littyl.digix.ui.screens.ActivityScreen
import com.littyl.digix.ui.screens.AssistantSetupScreen
import com.littyl.digix.ui.screens.EditScreen
import com.littyl.digix.ui.screens.HomeScreen
import com.littyl.digix.ui.screens.PickerScreen
import com.littyl.digix.ui.screens.SettingsScreen
import com.littyl.digix.ui.screens.SosSetupScreen
import com.littyl.digix.ui.screens.SetupScreen
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.DigixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Digix is dark in every mode, so the system bars always carry light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        GoogleHome.attach(this)
        setContent {
            DigixTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Digix.colors.background),
                ) {
                    val onboarded by Store.onboarded.collectAsStateWithLifecycle()
                    AnimatedContent(
                        targetState = onboarded,
                        transitionSpec = {
                            fadeIn(tween(280)) togetherWith fadeOut(tween(160))
                        },
                        label = "root",
                    ) { ready ->
                        if (ready) AppNavigation() else OnboardingScreen(onFinished = { Store.setOnboarded(true) })
                    }
                    // Scrolling content passes behind this, so it never collides with the status bar.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(Digix.colors.background),
                    )
                }
            }
        }
    }
}

/** A route is a string, so the back stack survives rotation and process restarts. */
private object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val PICKER = "picker"
    fun edit(code: String) = "edit:$code"
    fun new(action: ActionType) = "new:${action.name}"
    fun setup(action: ActionType) = "setup:${action.name}"
}

@Composable
private fun AppNavigation() {
    var stack by rememberSaveable(saver = listSaver(save = { it.value }, restore = { mutableStateOf(it.toList()) })) {
        mutableStateOf(listOf(Routes.HOME))
    }
    val push: (String) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    val popTo: (String) -> Unit = { target -> stack = stack.takeWhile { it != target } + target }

    BackHandler(enabled = stack.size > 1, onBack = pop)

    val reduce = Digix.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val depth = stack.size
    AnimatedContent(
        targetState = stack.last() to depth,
        transitionSpec = { pushTransition(reduce, rtl) },
        contentKey = { it.first },
        label = "navigation",
    ) { (route, _) ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Digix.colors.background),
        ) {
            when {
                route == Routes.HOME -> HomeScreen(
                    onOpenCode = { push(Routes.edit(it)) },
                    onAdd = { push(Routes.PICKER) },
                    onNew = { push(Routes.new(it)) },
                    onActivity = { push(Routes.ACTIVITY) },
                    onSettings = { push(Routes.SETTINGS) },
                )
                route == Routes.ACTIVITY -> ActivityScreen(onBack = pop)
                route == Routes.SETTINGS -> SettingsScreen(
                    onBack = pop,
                    onOpenHelper = { push(Routes.setup(ActionType.LOCK_PHONE)) },
                    onOpenAssistant = { push(Routes.setup(ActionType.ASSISTANT)) },
                )
                route == Routes.PICKER -> PickerScreen(
                    onBack = pop,
                    onPick = { action -> push(Routes.new(action)) },
                )
                route.startsWith("edit:") -> EditScreen(
                    existingCode = route.removePrefix("edit:"),
                    newAction = null,
                    onBack = pop,
                    onDone = { popTo(Routes.HOME) },
                    onSetup = { push(Routes.setup(it)) },
                )
                route.startsWith("new:") -> EditScreen(
                    existingCode = null,
                    newAction = ActionType.valueOf(route.removePrefix("new:")),
                    onBack = pop,
                    onDone = { popTo(Routes.HOME) },
                    onSetup = { push(Routes.setup(it)) },
                )
                route == Routes.setup(ActionType.ASSISTANT) -> AssistantSetupScreen(onBack = pop)
                route == Routes.setup(ActionType.SOS) -> SosSetupScreen(onBack = pop)
                route.startsWith("setup:") -> SetupScreen(
                    action = ActionType.valueOf(route.removePrefix("setup:")),
                    onBack = pop,
                )
            }
        }
    }
}

/** Depth is spatial: deeper screens arrive from the trailing edge, as a push does on Apple platforms. */
private fun AnimatedContentTransitionScope<Pair<String, Int>>.pushTransition(reduce: Boolean, rtl: Boolean): ContentTransform {
    if (reduce) return fadeIn(tween(120)) togetherWith fadeOut(tween(120))
    val forward = targetState.second >= initialState.second
    val edge = if (rtl) -1 else 1
    val spec = DigixMotion.smooth(IntOffset.VisibilityThreshold)
    val enter = slideInHorizontally(spec) { full -> if (forward) full * edge else -(full / 4) * edge }
    val exit = slideOutHorizontally(spec) { full -> if (forward) -(full / 4) * edge else full * edge }
    return ContentTransform(
        targetContentEnter = enter + fadeIn(tween(200)),
        initialContentExit = exit + fadeOut(tween(200)),
        targetContentZIndex = targetState.second.toFloat(),
    )
}
