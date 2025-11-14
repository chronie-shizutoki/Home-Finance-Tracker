package com.chronie.homemoney.ui.main

import androidx.lifecycle.ViewModel
import com.chronie.homemoney.core.common.DeveloperMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 主界面 ViewModel
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val developerMode: DeveloperMode
) : ViewModel() {
    
    /**
     * 开发者模式状态
     */
    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled
}
