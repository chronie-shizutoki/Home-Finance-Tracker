package com.chronie.homemoney.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.OutlinedButton
import androidx.core.net.toUri
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class LibraryInfo(
    val name: String,
    val version: String,
    val license: String,
    val licenseUrl: String,
    val projectUrl: String
)

val libraries = listOf(
    LibraryInfo(
        name = "Kotlin Stdlib",
        version = "2.4.10",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://kotlinlang.org/"
    ),
    LibraryInfo(
        name = "Kotlin Coroutines Android",
        version = "1.11.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    LibraryInfo(
        name = "AndroidX Core KTX",
        version = "1.19.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/core"
    ),
    LibraryInfo(
        name = "AndroidX AppCompat",
        version = "1.7.1",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/appcompat"
    ),
    LibraryInfo(
        name = "AndroidX CoordinatorLayout",
        version = "1.3.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/coordinatorlayout"
    ),
    LibraryInfo(
        name = "AndroidX Core Splashscreen",
        version = "1.2.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/core"
    ),
    LibraryInfo(
        name = "AndroidX Activity Compose",
        version = "1.13.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/activity"
    ),
    LibraryInfo(
        name = "Jetpack Compose BOM",
        version = "2026.06.01",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/compose-bom"
    ),
    LibraryInfo(
        name = "M3Color",
        version = "2026.1",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/Kyant0/M3Color"
    ),
    LibraryInfo(
        name = "Google Material Components",
        version = "1.14.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/material-components/material-components-android"
    ),
    LibraryInfo(
        name = "AndroidX Material3",
        version = "1.5.0-alpha24",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/compose-material3"
    ),
    LibraryInfo(
        name = "Miuix UI",
        version = "0.9.3",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/mizure/miuix-kmp"
    ),
    LibraryInfo(
        name = "Miuix Blur",
        version = "0.9.3",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/mizure/miuix-kmp"
    ),
    LibraryInfo(
        name = "Miuix Preference",
        version = "0.9.3",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/mizure/miuix-kmp"
    ),
    LibraryInfo(
        name = "AndroidX Lifecycle Runtime Compose",
        version = "2.11.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
    ),
    LibraryInfo(
        name = "AndroidX Lifecycle ViewModel Compose",
        version = "2.11.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
    ),
    LibraryInfo(
        name = "AndroidX Navigation Compose",
        version = "2.9.8",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/navigation"
    ),
    LibraryInfo(
        name = "Dagger Hilt Android",
        version = "2.60.1",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://dagger.dev/hilt/"
    ),
    LibraryInfo(
        name = "AndroidX Hilt Navigation Compose",
        version = "1.4.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/hilt"
    ),
    LibraryInfo(
        name = "AndroidX Datastore Preferences",
        version = "1.2.1",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/datastore"
    ),
    LibraryInfo(
        name = "AndroidX Room Runtime",
        version = "2.8.4",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/room"
    ),
    LibraryInfo(
        name = "AndroidX Room KTX",
        version = "2.8.4",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/room"
    ),
    LibraryInfo(
        name = "Retrofit",
        version = "3.0.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/square/retrofit"
    ),
    LibraryInfo(
        name = "Retrofit Gson Converter",
        version = "3.0.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/square/retrofit/tree/master/retrofit-converters/gson"
    ),
    LibraryInfo(
        name = "OkHttp Logging Interceptor",
        version = "5.4.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/square/okhttp"
    ),
    LibraryInfo(
        name = "AndroidX Paging Runtime KTX",
        version = "3.5.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/paging"
    ),
    LibraryInfo(
        name = "AndroidX Paging Compose",
        version = "3.5.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/paging"
    ),
    LibraryInfo(
        name = "Coil Compose",
        version = "3.5.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/coil-kt/coil"
    ),
    LibraryInfo(
        name = "AndroidX Security Crypto",
        version = "1.1.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/security"
    ),
    LibraryInfo(
        name = "SQLCipher Android",
        version = "4.17.0",
        license = "BSD 3-Clause License",
        licenseUrl = "https://opensource.org/licenses/BSD-3-Clause",
        projectUrl = "https://www.zetetic.net/sqlcipher/"
    ),
    LibraryInfo(
        name = "AndroidX SQLite",
        version = "2.7.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/sqlite"
    ),
    LibraryInfo(
        name = "AndroidX Work Runtime KTX",
        version = "2.11.2",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/work"
    ),
    LibraryInfo(
        name = "AndroidX Hilt Work",
        version = "1.4.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/hilt"
    ),
    LibraryInfo(
        name = "FastExcel",
        version = "0.20.2",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/dhatim/fastexcel"
    ),
    LibraryInfo(
        name = "FastExcel Reader",
        version = "0.20.2",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/dhatim/fastexcel"
    ),
    LibraryInfo(
        name = "Aalto XML",
        version = "1.4.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/FasterXML/aalto-xml"
    ),
    LibraryInfo(
        name = "XZ",
        version = "1.12",
        license = "Public Domain",
        licenseUrl = "https://tukaani.org/xz/legal.html",
        projectUrl = "https://tukaani.org/xz/"
    ),
    LibraryInfo(
        name = "UCrop",
        version = "2.2.11",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/Yalantis/uCrop"
    ),
    LibraryInfo(
        name = "MockK",
        version = "1.14.11",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://mockk.io/"
    ),
    LibraryInfo(
        name = "Kotlin Coroutines Test",
        version = "1.11.0",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    LibraryInfo(
        name = "Material Icons Extended",
        version = "BOM",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/compose-material"
    ),
    LibraryInfo(
        name = "Charty",
        version = "3.0.0-rc01",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/himanshoe/charty"
    ),
    LibraryInfo(
        name = "gRPC OkHttp",
        version = "1.82.2",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/grpc/grpc-java"
    ),
    LibraryInfo(
        name = "gRPC Protobuf Lite",
        version = "1.82.2",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/grpc/grpc-java"
    ),
    LibraryInfo(
        name = "gRPC Stub",
        version = "1.82.2",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        projectUrl = "https://github.com/grpc/grpc-java"
    ),
    LibraryInfo(
        name = "Protobuf JavaLite",
        version = "4.35.1",
        license = "BSD 3-Clause",
        licenseUrl = "https://github.com/protocolbuffers/protobuf/blob/main/LICENSE",
        projectUrl = "https://github.com/protocolbuffers/protobuf"
    ),
    LibraryInfo(
        name = "Javax Annotation API",
        version = "1.3.2",
        license = "CDDL 1.0 / GPL 2.0 with Classpath Exception",
        licenseUrl = "https://github.com/javaee/javax.annotation/blob/master/LICENSE",
        projectUrl = "https://github.com/javaee/javax.annotation"
    ),
    LibraryInfo(
        name = "JUnit",
        version = "4.13.2",
        license = "Eclipse Public License 1.0",
        licenseUrl = "https://www.eclipse.org/legal/epl-v10.html",
        projectUrl = "https://junit.org/junit4/"
    )
)

@Composable
fun OpenSourceLicensesScreen(
    context: Context,
    onNavigateBack: () -> Unit = {}
) {

    val scrollBehavior = MiuixScrollBehavior()

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(libraries) { library ->
                LibraryCard(
                    library = library,
                    context = context
                )
            }
        }
    }
}

@Composable
fun LibraryCard(
    library: LibraryInfo,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = library.name,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Version: ${library.version}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "License: ${library.license}",
                style = MiuixTheme.textStyles.body2
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, library.licenseUrl.toUri())
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
                            val intent = Intent(Intent.ACTION_VIEW, library.projectUrl.toUri())
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