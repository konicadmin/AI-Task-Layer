package com.localskills.app.ui.runner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localskills.app.ui.library.LibraryScreen

/**
 * Self-contained nav graph wiring the Library and Runner screens.
 *
 * P5-WIRING: in MainActivity, replace the placeholder HomeScreen with
 * `LocalSkillsNavHost()` (no parameters needed). The Library is the
 * start destination. ShareReceiverActivity should also pass through
 * here once the share-to-runner handoff is implemented (see the
 * `// TODO(P1): hand off to skill runner / picker` marker in
 * ShareReceiverActivity — it can launch MainActivity with a deep-link
 * extra carrying skillId + capture, and the host below pops to
 * `runner/{skillId}`).
 *
 * Routes:
 *   library            — start destination
 *   runner/{skillId}   — input form + review for a specific skill
 */
@Composable
fun LocalSkillsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_LIBRARY,
        modifier = modifier,
    ) {
        composable(ROUTE_LIBRARY) {
            LibraryScreen(
                onRun = { skillId -> navController.navigate(routeRunner(skillId)) },
            )
        }
        composable(ROUTE_RUNNER_PATTERN) { backStack ->
            val skillId = backStack.arguments?.getString(ARG_SKILL_ID).orEmpty()
            RunSkillScreen(
                skillId = skillId,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

private const val ARG_SKILL_ID = "skillId"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_RUNNER_PATTERN = "runner/{$ARG_SKILL_ID}"
private fun routeRunner(skillId: String): String = "runner/$skillId"
