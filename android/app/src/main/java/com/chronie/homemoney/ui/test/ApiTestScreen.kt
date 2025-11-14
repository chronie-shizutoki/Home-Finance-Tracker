package com.chronie.homemoney.ui.test

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTestScreen(
    context: Context,
    viewModel: ApiTestViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val testResults by viewModel.testResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    
    LaunchedEffect(testResults.size) {
        if (testResults.isNotEmpty()) {
            listState.animateScrollToItem(testResults.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.api_test)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.testHealthCheck() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(context.getString(R.string.health_check))
                }
                
                Button(
                    onClick = { viewModel.testGetExpenses() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(context.getString(R.string.get_expenses))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.testGetSubscriptionPlans() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(context.getString(R.string.subscription_plans))
                }
                
                Button(
                    onClick = { viewModel.runAllTests() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(context.getString(R.string.run_all_tests))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { viewModel.clearResults() },
                enabled = !isLoading && testResults.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.clear_results))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (testResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.click_to_start_test),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(testResults) { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    result.startsWith("✓") -> MaterialTheme.colorScheme.primary
                                    result.startsWith("✗") -> MaterialTheme.colorScheme.error
                                    result.startsWith("===") -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
