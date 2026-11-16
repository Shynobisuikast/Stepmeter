package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.components.InAppGoalBanner
import com.example.ui.navigation.AnalyticsRoute
import com.example.ui.navigation.BackupSettingsRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.GoalsRoute
import com.example.ui.navigation.ShareRoute
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BackupSettingsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.ShareScreen
import com.example.ui.theme.StepmeterTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { StepmeterApp(viewModel = viewModel) }
  }
}

private data class NavTab(
  val route: Any,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

@Composable
fun StepmeterApp(viewModel: MainViewModel) {
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val todayActivity by viewModel.todayActivity.collectAsStateWithLifecycle()
  val selectedYearMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
  val analyticsMetric by viewModel.analyticsMetric.collectAsStateWithLifecycle()
  val last30DaysPoints by viewModel.last30DaysChartPoints.collectAsStateWithLifecycle()
  val monthlyPoints by viewModel.monthlyChartPoints.collectAsStateWithLifecycle()
  val goalProgress by viewModel.goalProgressList.collectAsStateWithLifecycle()
  val notifications by viewModel.notifications.collectAsStateWithLifecycle()
  val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
  val activeGoalAlert by viewModel.activeGoalAlert.collectAsStateWithLifecycle()
  val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
  val feedbacks by viewModel.feedbacks.collectAsStateWithLifecycle()
  val isBackgroundTracking by viewModel.isBackgroundTracking.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  var hasActivityPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val permissionsToRequest = remember {
    buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }.toTypedArray()
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      val activityGranted =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          results[Manifest.permission.ACTIVITY_RECOGNITION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
              PackageManager.PERMISSION_GRANTED
        } else {
          true
        }
      hasActivityPermission = activityGranted
      if (activityGranted) {
        viewModel.startStepTracking(enableBackgroundService = true)
      }
    }

  LaunchedEffect(hasActivityPermission) {
    if (hasActivityPermission && viewModel.sensorTracker.hasHardwareSensor) {
      viewModel.startStepTracking(enableBackgroundService = true)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.statusBanner.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }

  LaunchedEffect(activeGoalAlert) {
    if (activeGoalAlert != null) {
      delay(5000)
      viewModel.dismissActiveGoalAlert()
    }
  }

  val tabs =
    listOf(
      NavTab(
        route = DashboardRoute,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.DirectionsWalk,
        unselectedIcon = Icons.Outlined.DirectionsWalk,
        tag = "nav_tab_dashboard",
      ),
      NavTab(
        route = AnalyticsRoute,
        labelRes = R.string.nav_analytics,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        tag = "nav_tab_analytics",
      ),
      NavTab(
        route = GoalsRoute,
        labelRes = R.string.nav_goals,
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        tag = "nav_tab_goals",
      ),
      NavTab(
        route = BackupSettingsRoute,
        labelRes = R.string.nav_backup_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        tag = "nav_tab_backup",
      ),
    )

  StepmeterTheme(themeMode = themeMode) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isShareScreen = currentDest?.hasRoute<ShareRoute>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 600.dp

      Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
          if (!useNavRail && !isShareScreen) {
            NavigationBar {
              tabs.forEach { tab ->
                val selected =
                  when (tab.route) {
                    DashboardRoute -> currentDest?.hasRoute<DashboardRoute>() == true
                    AnalyticsRoute -> currentDest?.hasRoute<AnalyticsRoute>() == true
                    GoalsRoute -> currentDest?.hasRoute<GoalsRoute>() == true
                    BackupSettingsRoute -> currentDest?.hasRoute<BackupSettingsRoute>() == true
                    else -> false
                  }
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    navController.navigate(tab.route) {
                      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = {
                    val iconVec = if (selected) tab.selectedIcon else tab.unselectedIcon
                    if (tab.route == GoalsRoute && unreadCount > 0) {
                      BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                        Icon(iconVec, contentDescription = stringResource(tab.labelRes))
                      }
                    } else {
                      Icon(iconVec, contentDescription = stringResource(tab.labelRes))
                    }
                  },
                  label = { Text(stringResource(tab.labelRes)) },
                  modifier = Modifier.testTag(tab.tag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail && !isShareScreen) {
              NavigationRail {
                tabs.forEach { tab ->
                  val selected =
                    when (tab.route) {
                      DashboardRoute -> currentDest?.hasRoute<DashboardRoute>() == true
                      AnalyticsRoute -> currentDest?.hasRoute<AnalyticsRoute>() == true
                      GoalsRoute -> currentDest?.hasRoute<GoalsRoute>() == true
                      BackupSettingsRoute -> currentDest?.hasRoute<BackupSettingsRoute>() == true
                      else -> false
                    }
                  NavigationRailItem(
                    selected = selected,
                    onClick = {
                      navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    },
                    icon = {
                      val iconVec = if (selected) tab.selectedIcon else tab.unselectedIcon
                      if (tab.route == GoalsRoute && unreadCount > 0) {
                        BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                          Icon(iconVec, contentDescription = stringResource(tab.labelRes))
                        }
                      } else {
                        Icon(iconVec, contentDescription = stringResource(tab.labelRes))
                      }
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    modifier = Modifier.testTag("${tab.tag}_rail"),
                  )
                }
              }
            }

            NavHost(
              navController = navController,
              startDestination = DashboardRoute,
              modifier = Modifier.weight(1f).fillMaxSize(),
              enterTransition = {
                slideIntoContainer(
                  AnimatedContentTransitionScope.SlideDirection.Start,
                  tween(280),
                ) + fadeIn(tween(240))
              },
              exitTransition = {
                slideOutOfContainer(
                  AnimatedContentTransitionScope.SlideDirection.Start,
                  tween(260),
                ) + fadeOut(tween(200))
              },
              popEnterTransition = {
                slideIntoContainer(
                  AnimatedContentTransitionScope.SlideDirection.End,
                  tween(280),
                ) + fadeIn(tween(240))
              },
              popExitTransition = {
                slideOutOfContainer(
                  AnimatedContentTransitionScope.SlideDirection.End,
                  tween(260),
                ) + fadeOut(tween(200))
              },
            ) {
              composable<DashboardRoute> {
                DashboardScreen(
                  todayActivity = todayActivity,
                  userProfile = userProfile,
                  goals = goalProgress,
                  hasHardwareSensor = viewModel.sensorTracker.hasHardwareSensor,
                  isBackgroundTracking = isBackgroundTracking,
                  needsActivityPermission =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityPermission,
                  onRequestActivityPermission = {
                    if (permissionsToRequest.isNotEmpty()) {
                      permissionLauncher.launch(permissionsToRequest)
                    }
                  },
                  onToggleBackgroundTracking = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityPermission) {
                      permissionLauncher.launch(permissionsToRequest)
                    } else {
                      viewModel.setBackgroundTrackingEnabled(enabled)
                    }
                  },
                  onNavigateToShare = { navController.navigate(ShareRoute) },
                  onNavigateToGoals = {
                    navController.navigate(GoalsRoute) {
                      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                )
              }

              composable<AnalyticsRoute> {
                AnalyticsScreen(
                  selectedYearMonth = selectedYearMonth,
                  selectedMetric = analyticsMetric,
                  last30DaysPoints = last30DaysPoints,
                  monthlyPoints = monthlyPoints,
                  onShiftMonth = viewModel::shiftSelectedMonth,
                  onSelectMetric = viewModel::setAnalyticsMetric,
                )
              }

              composable<GoalsRoute> {
                GoalsScreen(
                  goals = goalProgress,
                  notifications = notifications,
                  onSaveGoal = viewModel::saveGoal,
                  onToggleGoal = viewModel::toggleGoalActive,
                  onDeleteGoal = viewModel::deleteGoal,
                  onMarkAllRead = viewModel::markAllNotificationsRead,
                )
              }

              composable<BackupSettingsRoute> {
                BackupSettingsScreen(
                  themeMode = themeMode,
                  userProfile = userProfile,
                  localBackups = localBackups,
                  feedbacks = feedbacks,
                  onSetThemeMode = viewModel::setThemeMode,
                  onSaveProfile = viewModel::saveProfile,
                  onCreateLocalBackup = viewModel::createLocalBackup,
                  onExportBackupToUri = viewModel::exportEncryptedBackupToUri,
                  onRestoreLocalBackup = viewModel::restoreFromLocalBackup,
                  onRestoreUri = viewModel::restoreFromUri,
                  onDeleteLocalBackup = viewModel::deleteLocalBackup,
                  onSubmitFeedback = viewModel::submitFeedback,
                  onDeleteFeedback = viewModel::deleteFeedback,
                )
              }

              composable<ShareRoute> {
                ShareScreen(
                  buildCardData = viewModel::buildShareCardData,
                  onShareCard = viewModel::shareProgressCard,
                  onBack = { navController.popBackStack() },
                )
              }
            }
          }

          InAppGoalBanner(
            alert = activeGoalAlert,
            onDismiss = viewModel::dismissActiveGoalAlert,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
          )
        }
      }
    }
  }
}
