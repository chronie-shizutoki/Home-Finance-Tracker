package com.chronie.homemoney.ui.main

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Data class for Navigation Item Data
data class TabItemData(
    val icon: @Composable () -> Unit,
    val label: String,
    val index: Int
)

@Composable
fun BottomNavigationBar(
    context: Context,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    // Get current theme mode
    val isDarkTheme = isSystemInDarkTheme()
    
    // Navigation bar background color: semi-transparent effect for dark theme, opaque background for light theme
    val backgroundColor = if (isDarkTheme) Color(0x991E1E1E) else Color(0x99F5F5F5)
    
    // Unselected item color: black for light theme, white for dark theme
    val unselectedColor = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF666666)
    
    // Selected item color: primary color from theme colors
    val selectedColor = MiuixTheme.colorScheme.primary
    
    // Selected item background color: primaryContainer color from theme colors, semi-transparent effect
    val selectedBackgroundColor = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    
    // Define navigation items
    val navigationItems = listOf(
        TabItemData(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = context.getString(R.string.expense_list_title),
            index = 0
        ),
        TabItemData(
            icon = { Icon(Icons.Default.InsertChart, contentDescription = null) },
            label = context.getString(R.string.charts_title),
            index = 1
        ),
        TabItemData(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = context.getString(R.string.settings),
            index = 2
        )
    )
    
    BottomNavigationBarImpl(
        selectedTabIndex = selectedTab,
        onTabSelected = onTabChange,
        items = navigationItems,
        backgroundColor = backgroundColor,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        selectedBackgroundColor = selectedBackgroundColor
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope", "UseOfNonLambdaOffsetOverload")
@Composable
private fun BottomNavigationBarImpl(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<TabItemData>,
    backgroundColor: Color,
    selectedColor: Color,
    unselectedColor: Color,
    selectedBackgroundColor: Color
) {
    val animationScope = rememberCoroutineScope()
    var currentIndex by remember { mutableIntStateOf(selectedTabIndex) }
    
    // Animation for background position
    val backgroundPosition = remember {
        Animatable(selectedTabIndex.toFloat())
    }
    
    // Listen for selected tab index changes
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex != currentIndex) {
            currentIndex = selectedTabIndex
            backgroundPosition.snapTo(selectedTabIndex.toFloat())
        } else if (backgroundPosition.value != selectedTabIndex.toFloat()) {
            // Ensure animation position matches selected index (handle component recreation)
            backgroundPosition.snapTo(selectedTabIndex.toFloat())
        }
    }
    
    // Custom bottom navigation bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.height(64.dp)
        ) {
            val density = LocalDensity.current
            
            // Calculate tab item width (based on content)
            val tabWidth = 80.dp
            val totalWidth = tabWidth * items.size + 16.dp
            val tabWidthPx = with(density) { tabWidth.toPx() }
            
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(64.dp)
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                // Selected item background, moves across the entire Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .offset(
                            x = with(density) {
                                val animatedPosition = backgroundPosition.value
                                (animatedPosition * tabWidthPx).toDp()
                            },
                            y = 0.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .background(
                                color = selectedBackgroundColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
                
                // Option content layer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            TabItem(
                                item = item,
                                isSelected = currentIndex == item.index,
                                selectedColor = selectedColor,
                                unselectedColor = unselectedColor,
                                onTabSelected = {
                                    animationScope.launch {
                                        backgroundPosition.animateTo(
                                            targetValue = item.index.toFloat(),
                                            animationSpec = spring(
                                                dampingRatio = 0.7f,
                                                stiffness = 300f
                                            )
                                        )
                                        currentIndex = item.index
                                        onTabSelected(item.index)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

@Composable
private fun TabItem(
    item: TabItemData,
    isSelected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onTabSelected: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onTabSelected()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier
                .size(24.dp)
                .padding(bottom = 2.dp),
            imageVector = when (item.index) {
                0 -> Icons.Default.Home
                1 -> Icons.Default.InsertChart
                2 -> Icons.Default.Settings
                else -> Icons.Default.Home
            },
            contentDescription = item.label,
            tint = if (isSelected) selectedColor else unselectedColor
        )
        
        Text(
            text = item.label,
            style = MiuixTheme.textStyles.footnote2,
            color = if (isSelected) selectedColor else unselectedColor
        )
    }
}