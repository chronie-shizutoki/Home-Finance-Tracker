package com.chronie.homemoney.ui.membership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.data.local.PreferencesManager
import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.model.SubscriptionStatus
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.domain.usecase.GetMembershipStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MembershipViewModel @Inject constructor(
    private val getMembershipStatusUseCase: GetMembershipStatusUseCase,
    private val getSubscriptionPlansUseCase: com.chronie.homemoney.domain.usecase.GetSubscriptionPlansUseCase,
    private val checkLoginStatusUseCase: CheckLoginStatusUseCase,
    private val purchaseSubscriptionUseCase: com.chronie.homemoney.domain.usecase.PurchaseSubscriptionUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MembershipUiState>(MembershipUiState.Loading)
    val uiState: StateFlow<MembershipUiState> = _uiState.asStateFlow()

    init {
        loadMembershipData()
    }

    fun loadMembershipData() {
        viewModelScope.launch {
            _uiState.value = MembershipUiState.Loading
            
            try {
                val username = checkLoginStatusUseCase.getUsername()
                if (username.isNullOrEmpty()) {
                    _uiState.value = MembershipUiState.Error("未登录")
                    return@launch
                }
                
                // 获取会员套餐列表
                val plansResult = getSubscriptionPlansUseCase()
                if (plansResult.isFailure) {
                    _uiState.value = MembershipUiState.Error(
                        plansResult.exceptionOrNull()?.message ?: "获取套餐列表失败"
                    )
                    return@launch
                }
                
                // 获取当前会员状态
                val statusResult = getMembershipStatusUseCase(username, forceRefresh = false)
                val currentStatus = statusResult.getOrNull()
                
                _uiState.value = MembershipUiState.Success(
                    plans = plansResult.getOrNull() ?: emptyList(),
                    currentStatus = currentStatus
                )
            } catch (e: Exception) {
                _uiState.value = MembershipUiState.Error(
                    e.message ?: "加载失败"
                )
            }
        }
    }

    fun refreshMembershipStatus() {
        viewModelScope.launch {
            try {
                val username = checkLoginStatusUseCase.getUsername()
                if (username.isNullOrEmpty()) {
                    return@launch
                }
                
                val statusResult = getMembershipStatusUseCase(username, forceRefresh = true)
                val currentStatus = statusResult.getOrNull()
                
                // 更新当前状态
                val currentState = _uiState.value
                if (currentState is MembershipUiState.Success) {
                    _uiState.value = currentState.copy(currentStatus = currentStatus)
                }
            } catch (e: Exception) {
                // 刷新失败时保持当前状态，不显示错误
            }
        }
    }

    fun purchaseMembership(plan: SubscriptionPlan, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val username = checkLoginStatusUseCase.getUsername()
                if (username.isNullOrEmpty()) {
                    _uiState.value = MembershipUiState.Error("未登录")
                    return@launch
                }
                
                // 设置购买中状态
                val currentState = _uiState.value
                if (currentState is MembershipUiState.Success) {
                    _uiState.value = currentState.copy(isPurchasing = true)
                }
                
                android.util.Log.d("MembershipViewModel", "开始购买订阅: username=$username, plan=${plan.name}, period=${plan.period}")
                
                // 调用购买订阅 UseCase
                val result = purchaseSubscriptionUseCase(username, plan)
                
                if (result.isSuccess) {
                    android.util.Log.d("MembershipViewModel", "购买成功")
                    
                    // 保存会员状态到本地
                    val endDate = System.currentTimeMillis() + (plan.duration.toLong() * 24 * 60 * 60 * 1000L)
                    preferencesManager.saveMembershipStatus(
                        isActive = true,
                        planName = plan.name,
                        endDate = endDate
                    )
                    
                    // 刷新会员状态
                    refreshMembershipStatus()
                    
                    // 导航到主界面
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()
                    android.util.Log.e("MembershipViewModel", "购买失败", error)
                    
                    // 恢复状态并显示错误
                    if (currentState is MembershipUiState.Success) {
                        _uiState.value = currentState.copy(isPurchasing = false)
                    }
                    
                    _uiState.value = MembershipUiState.Error(
                        error?.message ?: "购买失败"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MembershipViewModel", "购买异常", e)
                _uiState.value = MembershipUiState.Error(
                    e.message ?: "购买失败"
                )
            }
        }
    }

    fun logout(onLogout: () -> Unit) {
        preferencesManager.clearUsername()
        preferencesManager.clearMembershipStatus()
        onLogout()
    }
}

sealed class MembershipUiState {
    object Loading : MembershipUiState()
    data class Success(
        val plans: List<SubscriptionPlan>,
        val currentStatus: SubscriptionStatus?,
        val isPurchasing: Boolean = false
    ) : MembershipUiState()
    data class Error(val message: String) : MembershipUiState()
}
