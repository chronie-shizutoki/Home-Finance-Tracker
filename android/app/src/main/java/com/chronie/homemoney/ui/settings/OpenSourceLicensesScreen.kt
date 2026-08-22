package com.chronie.homemoney.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.OutlinedButton
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import androidx.core.net.toUri
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.json.JSONArray
import org.json.JSONObject

data class LicenseItem(
    val group: String,
    val name: String,
    val version: String,
    val fullName: String
) {
    val displayName: String
        get() = name.split("-").joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
    
    val license: String
        get() = when {
            group.startsWith("androidx") || group.startsWith("com.google.android") || 
            group.startsWith("org.jetbrains") || group.startsWith("com.squareup") ||
            group.startsWith("io.coil-kt") || group.startsWith("io.grpc") ||
            group.startsWith("android") -> "Apache License 2.0"
            group == "net.zetetic" -> "BSD 3-Clause License"
            group == "org.tukaani" -> "Public Domain"
            group == "com.google.protobuf" -> "BSD 3-Clause"
            group == "javax.annotation" -> "CDDL 1.0 / GPL 2.0"
            group.startsWith("io.mockk") -> "Apache License 2.0"
            group.startsWith("com.jaredsburrows") -> "Apache License 2.0"
            group.startsWith("org.dhatim") -> "Apache License 2.0"
            group.startsWith("com.fasterxml") -> "Apache License 2.0"
            group.startsWith("top.yukonga") -> "Apache License 2.0"
            group.startsWith("com.himanshoe") -> "Apache License 2.0"
            group.startsWith("org.opencv") -> "Apache License 2.0"
            group.startsWith("com.google.mlkit") -> "Apache License 2.0"
            else -> "Apache License 2.0"
        }
    
    val licenseUrl: String
        get() = when (license) {
            "Apache License 2.0" -> "https://www.apache.org/licenses/LICENSE-2.0"
            "BSD 3-Clause License" -> "https://opensource.org/licenses/BSD-3-Clause"
            "BSD 3-Clause" -> "https://opensource.org/licenses/BSD-3-Clause"
            "Public Domain" -> "https://tukaani.org/xz/legal.html"
            "CDDL 1.0 / GPL 2.0" -> "https://github.com/javaee/javax.annotation/blob/master/LICENSE"
            else -> "https://www.apache.org/licenses/LICENSE-2.0"
        }
    
    val projectUrl: String
        get() = when {
            group.startsWith("androidx") -> "https://developer.android.com/jetpack/androidx/releases/${name.substringBefore("-")}"
            group.startsWith("com.google.android.material") -> "https://github.com/material-components/material-components-android"
            group.startsWith("org.jetbrains.kotlin") -> "https://kotlinlang.org/"
            group.startsWith("org.jetbrains.kotlinx.coroutines") -> "https://github.com/Kotlin/kotlinx.coroutines"
            group.startsWith("com.squareup.retrofit2") -> "https://github.com/square/retrofit"
            group.startsWith("com.squareup.okhttp3") -> "https://github.com/square/okhttp"
            group.startsWith("io.coil-kt") -> "https://github.com/coil-kt/coil"
            group.startsWith("com.google.dagger") -> "https://dagger.dev/hilt/"
            group.startsWith("androidx.datastore") -> "https://developer.android.com/jetpack/androidx/releases/datastore"
            group.startsWith("androidx.room") -> "https://developer.android.com/jetpack/androidx/releases/room"
            group.startsWith("androidx.lifecycle") -> "https://developer.android.com/jetpack/androidx/releases/lifecycle"
            group.startsWith("androidx.navigation") -> "https://developer.android.com/jetpack/androidx/releases/navigation"
            group.startsWith("androidx.paging") -> "https://developer.android.com/jetpack/androidx/releases/paging"
            group.startsWith("androidx.work") -> "https://developer.android.com/jetpack/androidx/releases/work"
            group.startsWith("androidx.security") -> "https://developer.android.com/jetpack/androidx/releases/security"
            group.startsWith("net.zetetic") -> "https://www.zetetic.net/sqlcipher/"
            group.startsWith("io.grpc") -> "https://github.com/grpc/grpc-java"
            group.startsWith("com.google.protobuf") -> "https://github.com/protocolbuffers/protobuf"
            group.startsWith("io.mockk") -> "https://mockk.io/"
            group.startsWith("org.dhatim") -> "https://github.com/dhatim/fastexcel"
            group.startsWith("org.tukaani") -> "https://tukaani.org/xz/"
            group.startsWith("top.yukonga") -> "https://github.com/mizure/miuix-kmp"
            group.startsWith("com.himanshoe") -> "https://github.com/himanshoe/charty"
            group.startsWith("org.opencv") -> "https://opencv.org/"
            group.startsWith("com.google.mlkit") -> "https://developers.google.com/ml-kit/"
            group.startsWith("javax.annotation") -> "https://github.com/javaee/javax.annotation"
            group.startsWith("com.google.android.gms") -> "https://developer.android.com/google/play-services"
            group.startsWith("androidx.compose.material") -> "https://developer.android.com/jetpack/androidx/releases/compose-material"
            group.startsWith("androidx.compose.ui") -> "https://developer.android.com/jetpack/androidx/releases/compose"
            group.startsWith("androidx.activity") -> "https://developer.android.com/jetpack/androidx/releases/activity"
            group.startsWith("androidx.appcompat") -> "https://developer.android.com/jetpack/androidx/releases/appcompat"
            group.startsWith("androidx.core") -> "https://developer.android.com/jetpack/androidx/releases/core"
            group.startsWith("androidx.splashscreen") -> "https://developer.android.com/jetpack/androidx/releases/core"
            group.startsWith("androidx.coordinatorlayout") -> "https://developer.android.com/jetpack/androidx/releases/coordinatorlayout"
            group.startsWith("androidx.hilt") -> "https://developer.android.com/jetpack/androidx/releases/hilt"
            group.startsWith("androidx.sqlite") -> "https://developer.android.com/jetpack/androidx/releases/sqlite"
            else -> "https://central.sonatype.com/artifact/$group/$name"
        }
}

fun loadLicenses(context: Context): List<LicenseItem> {
    return try {
        val jsonString = context.assets.open("licenses.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        (0 until jsonArray.length()).map { i ->
            val obj = jsonArray.getJSONObject(i)
            LicenseItem(
                group = obj.getString("group"),
                name = obj.getString("name"),
                version = obj.getString("version"),
                fullName = obj.getString("fullName")
            )
        }.filter { it.group != "android" && it.name != "app" }
            .sortedBy { it.displayName }
    } catch (e: Exception) {
        android.util.Log.e("OpenSourceLicenses", "Failed to load licenses", e)
        emptyList()
    }
}

/**
 * Displays a scrollable list of open-source libraries used by the app.
 *
 * Loads license data from the bundled `licenses.json` asset file via [loadLicenses].
 * Each entry is rendered as a [LicenseCard] showing the library name, version,
 * license type, and buttons to open the license text or the project homepage in the browser.
 *
 * @param context Android [Context] used to read assets and launch external intents.
 * @param onNavigateBack callback to pop the back stack.
 */
@Composable
fun OpenSourceLicensesScreen(
    context: Context,
    onNavigateBack: () -> Unit = {}
) {
    val scrollBehavior = MiuixScrollBehavior()
    var licenses by remember { mutableStateOf<List<LicenseItem>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        licenses = loadLicenses(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(com.chronie.homemoney.R.string.open_source_licenses),
                largeTitle = context.getString(com.chronie.homemoney.R.string.open_source_licenses),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(com.chronie.homemoney.R.string.back))
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp))
                }
            )
        }
    ) { paddingValues ->
        val listState = rememberLazyListState()
        RegisterScrollToTop(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(licenses) { license ->
                LicenseCard(
                    license = license,
                    context = context
                )
            }
        }
    }
}

@Composable
fun LicenseCard(
    license: LicenseItem,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = license.displayName,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Version: ${license.version}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "License: ${license.license}",
                style = MiuixTheme.textStyles.body2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = license.fullName,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, license.licenseUrl.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Failed to open license: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("License")
                }
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, license.projectUrl.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Failed to open project: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Project")
                }
            }
        }
    }
}
