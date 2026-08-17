package com.chronie.homemoney

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.chronie.homemoney.ui.navigation.AppRoute
import com.chronie.homemoney.core.common.LanguageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.chronie.homemoney.ui.expense.AddExpenseScreen
import com.chronie.homemoney.ui.expense.AIExpenseScreen
import com.chronie.homemoney.ui.main.MainScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import com.chronie.homemoney.ui.settings.OpenSourceLicensesScreen
import com.chronie.homemoney.ui.sync.LanSyncScreen
import com.chronie.homemoney.ui.sync.IncomingSyncRequestDialog
import com.chronie.homemoney.data.sync.SyncRequestBus
import com.chronie.homemoney.ui.scroll.ScrollToTopRegistry
import com.chronie.homemoney.ui.test.DatabaseTestScreen
import com.chronie.homemoney.ui.theme.HomeMoneyTheme
import com.chronie.homemoney.ui.welcome.WelcomeScreen
import com.chronie.homemoney.ui.membership.MembershipScreen
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.service.HealthCheckService
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import java.util.Locale
import javax.inject.Inject

val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> {
    error("No LanguageManager provided")
}

/**
 * The single-Activity entry point for the Home Finance Tracker app.
 *
 * This Activity:
 * - Initialises the cloud sync scheduler and triggers an immediate sync on launch.
 * - Configures edge-to-edge rendering with automatic light/dark status bar icons.
 * - Starts the [HealthCheckService] for periodic background system checks.
 * - Observes the current language from [LanguageManager] and applies it to the
 *   [Configuration] so the entire Compose tree uses the correct locale resources.
 * - Sets [HomeMoneyApp] as the Compose content root, which hosts the
 *   [androidx.navigation3.ui.NavDisplay] for all screen navigation (welcome, main, settings, charts, etc.).
 */
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

        // Edge-to-edge with proper status bar icon colors based on theme
        // detectDarkMode ensures light mode → dark icons, dark mode → light icons
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { resources.configuration.isNightModeActive }
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { resources.configuration.isNightModeActive }
            )
        )

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

    // --- Tap status bar to scroll to top ---------------------------------------------------
    // Android exposes no public callback for "tap the status bar", so we watch touch events
    // in the status-bar band ourselves and forward a confirmed tap to ScrollToTopRegistry.
    // The gesture is never consumed, so normal touches still reach the UI underneath.

    private var statusBarTapDownX = 0f
    private var statusBarTapDownY = 0f
    private var statusBarTapConsumed = false

    /** Max pointer travel (px) that still counts as a tap rather than a drag. */
    private val statusBarTapSlop = 24f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                statusBarTapDownX = event.x
                statusBarTapDownY = event.y
                statusBarTapConsumed = false
            }
            MotionEvent.ACTION_UP -> {
                if (!statusBarTapConsumed) {
                    val top = statusBarTapDownY
                    val statusBarHeight = getStatusBarHeight()
                    val dy = abs(event.y - statusBarTapDownY)
                    val dx = abs(event.x - statusBarTapDownX)
                    if (statusBarHeight > 0 && top < statusBarHeight && dy < statusBarTapSlop && dx < statusBarTapSlop) {
                        ScrollToTopRegistry.trigger()
                        statusBarTapConsumed = true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /** Current status-bar height in px, with a framework-dimension fallback. */
    private fun getStatusBarHeight(): Int {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val fromInsets = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
        if (fromInsets != null && fromInsets > 0) return fromInsets
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}

/**
 * Root composable that owns the app-wide [androidx.navigation3.ui.NavDisplay] and top-level state.
 *
 * Determines the start destination based on login status (welcome vs. main).
 * Manages cross-screen state such as [shouldRefreshExpenses] and [selectedTab].
 * Listens for incoming LAN sync requests via [SyncRequestBus] and shows
 * [IncomingSyncRequestDialog] regardless of which screen is currently displayed.
 *
 * @param context the Android [Context] used for navigation callbacks and dialogs.
 * @param checkLoginStatusUseCase use case to determine whether the user is logged in.
 */
@Composable
fun HomeMoneyApp(
    context: Context,
    checkLoginStatusUseCase: CheckLoginStatusUseCase
) {
    val startRoute = remember {
        if (checkLoginStatusUseCase()) AppRoute.Main else AppRoute.Welcome
    }
    val backStack = rememberNavBackStack(startRoute)
    var shouldRefreshExpenses by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<AppRoute.Welcome> {
                Box(Modifier.fillMaxSize()) {
                    WelcomeScreen(
                        context = context,
                        onGetStartedClick = {
                            backStack.clear()
                            backStack.add(AppRoute.Main)
                        }
                    )
                }
            }

            entry<AppRoute.Membership> {
                Box(Modifier.fillMaxSize()) {
                    MembershipScreen(
                        context = context,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onLogout = {
                            backStack.clear()
                            backStack.add(AppRoute.Welcome)
                        }
                    )
                }
            }

            entry<AppRoute.Settings> {
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        context = context,
                        onNavigateToDatabaseTest = { backStack.add(AppRoute.DatabaseTest) },
                        onNavigateToLanSync = { backStack.add(AppRoute.LanSync) },
                        onNavigateToOpenSourceLicenses = { backStack.add(AppRoute.OpenSourceLicenses) },
                        onLogout = {
                            backStack.clear()
                            backStack.add(AppRoute.Welcome)
                        },
                        onRequireLogin = {
                            backStack.clear()
                            backStack.add(AppRoute.Welcome)
                        }
                    )
                }
            }

            entry<AppRoute.LanSync> {
                Box(Modifier.fillMaxSize()) {
                    LanSyncScreen(
                        context = context,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }

            entry<AppRoute.Main> {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        context = context,
                        shouldRefreshExpenses = shouldRefreshExpenses,
                        onRefreshHandled = { shouldRefreshExpenses = false },
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it },
                        onNavigateToDatabaseTest = {
                            selectedTab = 2
                            backStack.add(AppRoute.DatabaseTest)
                        },
                        onNavigateToAddExpense = {
                            selectedTab = 0
                            backStack.add(AppRoute.AddExpense())
                        },
                        onNavigateToAIExpense = {
                            selectedTab = 0
                            backStack.add(AppRoute.AIExpense)
                        },
                        onNavigateToEditExpense = { expenseId ->
                            selectedTab = 0
                            backStack.add(AppRoute.AddExpense(expenseId))
                        },
                        onNavigateToWeekdayDetail = { dayOfWeek, amount, count, percentage, startDate, endDate ->
                            selectedTab = 1
                            backStack.add(
                                AppRoute.WeekdayDetail(
                                    dayOfWeek = dayOfWeek,
                                    amount = amount,
                                    count = count,
                                    percentage = percentage,
                                    startDate = startDate,
                                    endDate = endDate
                                )
                            )
                        },
                        onNavigateToLanSync = {
                            selectedTab = 2
                            backStack.add(AppRoute.LanSync)
                        },
                        onNavigateToOpenSourceLicenses = {
                            selectedTab = 2
                            backStack.add(AppRoute.OpenSourceLicenses)
                        },
                        onRequireLogin = {
                            backStack.clear()
                            backStack.add(AppRoute.Welcome)
                        }
                    )
                }
            }

            entry<AppRoute.AddExpense> { key ->
                Box(Modifier.fillMaxSize()) {
                    AddExpenseScreen(
                        context = context,
                        expenseId = key.expenseId,
                        onNavigateBack = {
                            shouldRefreshExpenses = true
                            backStack.removeLastOrNull()
                        }
                    )
                }
            }

            entry<AppRoute.AIExpense> {
                Box(Modifier.fillMaxSize()) {
                    AIExpenseScreen(
                        context = context,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onRecordsSaved = {
                            shouldRefreshExpenses = true
                            backStack.removeLastOrNull()
                        }
                    )
                }
            }

            entry<AppRoute.DatabaseTest> {
                Box(Modifier.fillMaxSize()) {
                    DatabaseTestScreen(
                        context = context,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }

            entry<AppRoute.OpenSourceLicenses> {
                Box(Modifier.fillMaxSize()) {
                    OpenSourceLicensesScreen(
                        context = context,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }

            entry<AppRoute.WeekdayDetail> { key ->
                Box(Modifier.fillMaxSize()) {
                    com.chronie.homemoney.ui.charts.WeekdayDetailScreen(
                        context = context,
                        dayOfWeek = key.dayOfWeek,
                        amount = key.amount,
                        count = key.count,
                        percentage = key.percentage,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        },
        transitionSpec = {
            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } togetherWith
                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it }
        },
        popTransitionSpec = {
            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 4 } togetherWith
                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 4 } togetherWith
                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
        }
    )

    // App-wide incoming LAN sync confirmation.
    // The responder runs on a native worker thread and, when no screen-bound callback is
    // installed, posts the request to SyncRequestBus. Observing it here (at the always-mounted
    // app root) means a sync request arriving on ANY screen still prompts the user instead of
    // being silently rejected - the original "B does nothing, A shows 100% failed" symptom.
    val busRequest by SyncRequestBus.request.collectAsState()
    if (busRequest != null) {
        IncomingSyncRequestDialog(
            context = context,
            requestInfo = busRequest!!,
            onAccept = { SyncRequestBus.resolve(true) },
            onReject = { SyncRequestBus.resolve(false) }
        )
    }
}
