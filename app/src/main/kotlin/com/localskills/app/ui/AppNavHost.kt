package com.localskills.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localskills.app.skill.manifest.SkillManifest
import com.localskills.app.ui.builder.BuilderScreen
import com.localskills.app.ui.library.LibraryScreen
import com.localskills.app.ui.runner.RunSkillScreen
import com.localskills.app.ui.share.ExportSkillScreen
import com.localskills.app.ui.share.ExportSkillViewModel
import dagger.hilt.android.EntryPointAccessors

/**
 * Top-level Compose nav graph. The library is the start destination; every
 * other screen is reached from there.
 *
 * Routes:
 *   library            — installed skills, source of run / share / build CTAs
 *   runner/{skillId}   — input form + review for a specific skill
 *   builder            — form-driven skill authoring
 *   export/{skillId}   — share-text preview + share-as-file for a skill
 */
@Composable
fun AppNavHost(
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
                onBuild = { navController.navigate(ROUTE_BUILDER) },
                onShare = { skillId -> navController.navigate(routeExport(skillId)) },
            )
        }
        composable(ROUTE_RUNNER_PATTERN) { backStack ->
            val skillId = backStack.arguments?.getString(ARG_SKILL_ID).orEmpty()
            RunSkillScreen(
                skillId = skillId,
                onDone = { navController.popBackStack() },
            )
        }
        composable(ROUTE_BUILDER) {
            BuilderScreen(onClose = { navController.popBackStack() })
        }
        composable(ROUTE_EXPORT_PATTERN) { backStack ->
            val skillId = backStack.arguments?.getString(ARG_SKILL_ID).orEmpty()
            ExportSkillRoute(
                skillId = skillId,
                onClose = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun ExportSkillRoute(
    skillId: String,
    onClose: () -> Unit,
    viewModel: ExportSkillViewModel = hiltViewModel(),
) {
    var manifest by remember { mutableStateOf<SkillManifest?>(null) }
    val context = LocalContext.current

    LaunchedEffect(skillId) {
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SkillRepositoryEntryPoint::class.java,
        ).skillRepository()
        manifest = repo.findById(skillId)?.manifest?.also(viewModel::bind)
    }

    if (manifest == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    BackHandler(onBack = onClose)
    ExportSkillScreen(viewModel = viewModel)
}

private const val ARG_SKILL_ID = "skillId"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_BUILDER = "builder"
private const val ROUTE_RUNNER_PATTERN = "runner/{$ARG_SKILL_ID}"
private const val ROUTE_EXPORT_PATTERN = "export/{$ARG_SKILL_ID}"
private fun routeRunner(skillId: String): String = "runner/$skillId"
private fun routeExport(skillId: String): String = "export/$skillId"
