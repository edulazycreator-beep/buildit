package com.sketchwareai.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sketchwareai.app.ui.create.CreateProjectScreen
import com.sketchwareai.app.ui.dashboard.DashboardScreen
import com.sketchwareai.app.ui.projects.ProjectListScreen
import com.sketchwareai.app.ui.settings.SettingsScreen
import com.sketchwareai.app.ui.workspace.WorkspaceScreen
import kotlinx.serialization.Serializable

/*
 * Type-safe destinations. Phase 1 ships Dashboard, Project Manager, Create Project, the
 * project Workspace shell and Settings. Later phases add Designer, Logic, Components,
 * Resources, Code, Build and AI destinations under the Workspace.
 */
@Serializable data object DashboardRoute
@Serializable data object ProjectsRoute
@Serializable data object CreateProjectRoute
@Serializable data class WorkspaceRoute(val projectId: String)
@Serializable data object SettingsRoute

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = DashboardRoute,
        enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
    ) {
        composable<DashboardRoute> {
            DashboardScreen(
                onCreateProject = { nav.navigate(CreateProjectRoute) },
                onOpenProject = { nav.navigate(WorkspaceRoute(it.value)) },
                onShowAllProjects = { nav.navigate(ProjectsRoute) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<ProjectsRoute> {
            ProjectListScreen(
                onBack = { nav.popBackStack() },
                onCreateProject = { nav.navigate(CreateProjectRoute) },
                onOpenProject = { nav.navigate(WorkspaceRoute(it.value)) },
            )
        }
        composable<CreateProjectRoute> {
            CreateProjectScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate(WorkspaceRoute(id.value)) { popUpTo<CreateProjectRoute> { inclusive = true } }
                },
            )
        }
        composable<WorkspaceRoute> {
            WorkspaceScreen(onBack = { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
