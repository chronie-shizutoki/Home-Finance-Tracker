package com.chronie.homemoney

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chronie.homemoney.core.common.LanguageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.chronie.homemoney.ui.expense.AddExpenseScreen
import com.chronie.homemoney.ui.expense.AIExpenseScreen
import com.chronie.homemoney.ui.main.MainScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import com.chronie.homemoney.ui.settings.OpenSourceLicensesScreen
import com.chronie.homemoney.ui.sync.LanSyncScreen
import com.chronie.homemoney.ui.test.DatabaseTestScreen
import com.chronie.homemoney.ui.theme.HomeMoneyTheme
import com.chronie.homemoney.ui.welcome.WelcomeScreen
import com.chronie.homemoney.ui.membership.MembershipScreen
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.service.HealthCheckService
import com.chronie.homemoney.ui.util.TransitionSpecs
import com.chronie.homemoney.ui.util.predictiveBackEffect
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> {
    error("No LanguageManager provided")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var languageManager: LanguageManager

    @Inject
    lateinit var syncScheduler: com.chronie.homemoney.data.sync.SyncScheduler

    @Inject
    lateinit var checkLoginStatusUseCase: CheckLoginStatusUseCase

    @Inject
    lateinit var healthCheckService: HealthCheckService

    @SuppressLint("LocalContextConfigurationRead")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize sync scheduler
        syncScheduler.initialize()

        // Trigger immediate sync on app start (allow failure)
        lifecycleScope.launch {
            try {
                syncScheduler.triggerImmediateSync()
            } catch (e: Exception) {
                // Sync failure does not affect app startup
                android.util.Log.w("MainActivity", "Failed to trigger sync on app start", e)
            }
        }

        // Switch to normal theme immediately to avoid splash screen background affecting Popup windows
        setTheme(R.style.AppTheme_NoActionBar)

        // Clear splash screen background, set to transparent background
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Make sure the window draws behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Start health check service
        healthCheckService.start()

        setContent {
            val currentLanguage by languageManager.currentLanguage.collectAsState()

            // Update configuration when language changes
            val context = LocalContext.current
            val locale = currentLanguage.locale
            Locale.setDefault(locale)
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            val localizedContext = context.createConfigurationContext(configuration)

            CompositionLocalProvider(
                LocalLanguageManager provides languageManager
            ) {
                HomeMoneyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MiuixTheme.colorScheme.background
                    ) {
                        HomeMoneyApp(
                            context = localizedContext,
                            checkLoginStatusUseCase = checkLoginStatusUseCase
                        )
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        if (::languageManager.isInitialized) {
            languageManager.checkAndApplySystemLanguage()
        }
    }

    override fun onResume() {
        super.onResume()
        languageManager.checkAndApplySystemLanguage()
    }

    override fun onDestroy() {
        super.onDestroy()
        healthCheckService.stop()
    }
}

@Composable
fun HomeMoneyApp(
    context: Context,
    checkLoginStatusUseCase: CheckLoginStatusUseCase
) {
    val navController = rememberNavController()
    var shouldRefreshExpenses by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Determine initial route
    val startDestination = remember {
        val isLoggedIn = checkLoginStatusUseCase()
        if (isLoggedIn) "main" else "welcome"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { TransitionSpecs.enterTransition() },
        exitTransition = { TransitionSpecs.exitTransition() },
        popEnterTransition = { TransitionSpecs.popEnterTransition() },
        popExitTransition = { TransitionSpecs.popExitTransition() }
    ) {
        composable("welcome") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                WelcomeScreen(
                    context = context,
                    onGetStartedClick = {
                        navController.navigate("main") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("membership") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                MembershipScreen(
                    context = context,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onLogout = {
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("settings") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                SettingsScreen(
                    context = context,
                    onNavigateToDatabaseTest = {
                        navController.navigate("database_test")
                    },
                    onNavigateToLanSync = {
                        navController.navigate("lan_sync")
                    },
                    onNavigateToOpenSourceLicenses = {
                        navController.navigate("open_source_licenses")
                    },
                    onLogout = {
                        // Logout, clear entire navigation stack and return to welcome page
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onRequireLogin = {
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("lan_sync") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                LanSyncScreen(
                    context = context,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("main") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                MainScreen(
                    context = context,
                    shouldRefreshExpenses = shouldRefreshExpenses,
                    onRefreshHandled = { shouldRefreshExpenses = false },
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onNavigateToDatabaseTest = {
                        selectedTab = 2
                        navController.navigate("database_test")
                    },
                    onNavigateToAddExpense = {
                        selectedTab = 0
                        navController.navigate("add_expense")
                    },
                    onNavigateToEditExpense = { expenseId ->
                        selectedTab = 0
                        navController.navigate(
                            route = "add_expense?expenseId=$expenseId"
                        )
                    },
                    onNavigateToWeekdayDetail = { dayOfWeek, amount, count, percentage, startDate, endDate ->
                        selectedTab = 1
                        navController.navigate(
                            route = "weekday_detail?dayOfWeek=$dayOfWeek&amount=$amount&count=$count&percentage=$percentage&startDate=$startDate&endDate=$endDate"
                        )
                    },
                    onNavigateToLanSync = {
                        selectedTab = 2
                        navController.navigate("lan_sync")
                    },
                    onNavigateToOpenSourceLicenses = {
                        selectedTab = 2
                        navController.navigate("open_source_licenses")
                    },
                    onRequireLogin = {
                        // Logout, clear entire navigation stack and return to welcome page
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(
            "add_expense?expenseId={expenseId}",
            arguments = listOf(
                navArgument("expenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId")
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                AddExpenseScreen(
                    context = context,
                    expenseId = expenseId,
                    onNavigateBack = {
                        shouldRefreshExpenses = true
                        navController.popBackStack()
                    },
                    onNavigateToAI = {
                        navController.navigate("ai_expense")
                    }
                )
            }
        }

        composable("ai_expense") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                AIExpenseScreen(
                    context = context,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onRecordsSaved = {
                        shouldRefreshExpenses = true
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("database_test") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                DatabaseTestScreen(
                    context = context,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("open_source_licenses") {
            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                OpenSourceLicensesScreen(
                    context = context,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            "weekday_detail?dayOfWeek={dayOfWeek}&amount={amount}&count={count}&percentage={percentage}&startDate={startDate}&endDate={endDate}",
            arguments = listOf(
                navArgument("dayOfWeek") { type = NavType.IntType },
                navArgument("amount") { type = NavType.FloatType },
                navArgument("count") { type = NavType.IntType },
                navArgument("percentage") { type = NavType.FloatType },
                navArgument("startDate") { type = NavType.StringType },
                navArgument("endDate") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val dayOfWeek = backStackEntry.arguments?.getInt("dayOfWeek") ?: 0
            val amount = backStackEntry.arguments?.getFloat("amount")?.toDouble() ?: 0.0
            val count = backStackEntry.arguments?.getInt("count") ?: 0
            val percentage = backStackEntry.arguments?.getFloat("percentage") ?: 0f

            Box(Modifier.fillMaxSize().predictiveBackEffect(this)) {
                com.chronie.homemoney.ui.charts.WeekdayDetailScreen(
                    context = context,
                    dayOfWeek = dayOfWeek,
                    amount = amount,
                    count = count,
                    percentage = percentage,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
