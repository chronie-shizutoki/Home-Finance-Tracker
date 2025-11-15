package com.chronie.homemoney.ui.membership

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.model.SubscriptionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipPurchaseScreen(
    context: Context,
    onNavigateToMain: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    viewModel: MembershipViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 显示错误提示
    LaunchedEffect(uiState) {
        if (uiState is MembershipUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (uiState as MembershipUiState.Error).message,
                duration = SnackbarDuration.Long
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.membership_title)) },
                actions = {
                    IconButton(onClick = { viewModel.logout(onNavigateToWelcome) }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = context.getString(R.string.auth_logout_button)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is MembershipUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is MembershipUiState.Success -> {
                MembershipContent(
                    context = context,
                    plans = state.plans,
                    currentStatus = state.currentStatus,
                    onPurchase = { plan ->
                        viewModel.purchaseMembership(plan, onNavigateToMain)
                    },
                    onRefresh = { viewModel.refreshMembershipStatus() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            is MembershipUiState.Error -> {
                ErrorContent(
                    context = context,
                    message = state.message,
                    onRetry = { viewModel.loadMembershipData() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun MembershipContent(
    context: Context,
    plans: List<SubscriptionPlan>,
    currentStatus: SubscriptionStatus?,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 会员过期提示
        if (currentStatus != null && !currentStatus.isActive) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.membership_expired),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        // 会员权益说明
        item {
            MembershipBenefitsCard(context)
        }
        
        // 当前会员状态
        if (currentStatus != null && currentStatus.isActive) {
            item {
                CurrentMembershipCard(context, currentStatus)
            }
        }
        
        // 会员套餐列表
        item {
            Text(
                text = context.getString(R.string.membership_plans_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        items(plans) { plan ->
            MembershipPlanCard(
                context = context,
                plan = plan,
                onPurchase = { onPurchase(plan) }
            )
        }
        
        // 刷新按钮
        item {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.membership_refresh))
            }
        }
    }
}

@Composable
private fun MembershipBenefitsCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.getString(R.string.membership_benefits_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            val benefits = listOf(
                context.getString(R.string.benefit_unlimited_expenses),
                context.getString(R.string.benefit_advanced_charts),
                context.getString(R.string.benefit_data_export),
                context.getString(R.string.benefit_cloud_sync),
                context.getString(R.string.benefit_priority_support)
            )
            
            benefits.forEach { benefit ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = benefit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentMembershipCard(
    context: Context,
    status: SubscriptionStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.getString(R.string.current_membership),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${context.getString(R.string.membership_plan)}: ${status.planName ?: "Unknown"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            status.endDate?.let { endDate ->
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                Text(
                    text = "${context.getString(R.string.membership_expires_on)}: ${dateFormat.format(java.util.Date(endDate))}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MembershipPlanCard(
    context: Context,
    plan: SubscriptionPlan,
    onPurchase: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "¥${plan.price}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            plan.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "${context.getString(R.string.duration)}: ${plan.duration} days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Button(
                onClick = onPurchase,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.purchase))
            }
        }
    }
}

@Composable
private fun ErrorContent(
    context: Context,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRetry) {
            Text(context.getString(R.string.retry))
        }
    }
}
