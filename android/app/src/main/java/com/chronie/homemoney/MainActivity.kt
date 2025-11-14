package com.chronie.homemoney

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chronie.homemoney.core.common.LanguageManager
import com.chronie.homemoney.ui.main.MainScreen
import com.chronie.homemoney.ui.settings.LanguageSettingsScreen
import com.chronie.homemoney.ui.test.DatabaseTestScreen
import com.chronie.homemoney.ui.theme.HomeMoneyTheme
import com.chronie.homemoney.ui.welcome.WelcomeScreen
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Make sure the window draws behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            val currentLanguage by languageManager.currentLanguage.collectAsState()
            
            // Update configuration when language changes
            val context = LocalContext.current
            val locale = currentLanguage.locale
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            val localizedContext = context.createConfigurationContext(configuration)
            
            CompositionLocalProvider(
                LocalLanguageManager provides languageManager
            ) {
                HomeMoneyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeMoneyApp(localizedContext)
                    }
                }
            }
        }
    }
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }
}

@Composable
fun HomeMoneyApp(context: Context) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(
                context = context,
                onLanguageSettingsClick = {
                    navController.navigate("language_settings")
                },
                onGetStartedClick = {
                    navController.navigate("main")
                }
            )
        }

        composable("language_settings") {
            LanguageSettingsScreen(
                context = context,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("main") {
            MainScreen(
                context = context,
                onNavigateToSettings = {
                    navController.navigate("language_settings")
                },
                onNavigateToDatabaseTest = {
                    navController.navigate("database_test")
                }
            )
        }
        
        composable("database_test") {
            DatabaseTestScreen(
                context = context,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
