package com.chronie.homemoney.ui.main

import androidx.lifecycle.ViewModel
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.domain.usecase.CheckMembershipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 主界面 ViewModel
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val developerMode: DeveloperMode,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase,
    private val checkMembershipUseCase: CheckMembershipUseCase
) : ViewModel() {
    
    /**
     * 开发者模式状态
     */
    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    private val _isMember = MutableStateFlow(false)
    val isMember: StateFlow<Boolean> = _isMember.asStateFlow()
    
    private val _shouldShowExpiryWarning = MutableStateFlow(false)
    val shouldShowExpiryWarning: StateFlow<Boolean> = _shouldShowExpiryWarning.asStateFlow()
    
    private val _daysUntilExpiry = MutableStateFlow(0)
    val daysUntilExpiry: StateFlow<Int> = _daysUntilExpiry.asStateFlow()
    
    init {
        checkAccess()
    }
    
    fun checkAccess() {
        _isLoggedIn.value = checkLoginStatusUseCase()
        _isMember.value = checkMembershipUseCase()
        
        // 检查会员到期时间
        val endDate = checkMembershipUseCase.getEndDate()
        if (endDate != null && _isMember.value) {
            val daysLeft = ((endDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            _daysUntilExpiry.value = daysLeft
            _shouldShowExpiryWarning.value = daysLeft in 1..7
        } else {
            _shouldShowExpiryWarning.value = false
            _daysUntilExpiry.value = 0
        }
    }
    
    fun dismissExpiryWarning() {
        _shouldShowExpiryWarning.value = false
    }
}
