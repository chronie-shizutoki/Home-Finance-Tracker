package com.chronie.homemoney.ui.expense

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.repository.AIRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * AI Expense Record View Model
 */
@HiltViewModel
class AIExpenseViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val aiRecordRepository: AIRecordRepository,
    private val syncScheduler: com.chronie.homemoney.data.sync.SyncScheduler
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AIExpenseUiState())
    val uiState: StateFlow<AIExpenseUiState> = _uiState.asStateFlow()
    
    /**
     * Add images to the UI state
     */
    fun addImages(uris: List<Uri>) {
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages + uris
            )
        }
    }
    
    /**
     * Remove image from the UI state
     */
    fun removeImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages - uri
            )
        }
    }
    
    /**
     * Update text input in the UI state
     */
    fun updateTextInput(text: String) {
        _uiState.update { it.copy(textInput = text) }
    }
    
    /**
     * Start recognition process
     */
    fun startRecognition() {
        val state = _uiState.value
        
        if (state.selectedImages.isEmpty() && state.textInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.ai_expense_no_input)) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val records = mutableListOf<AIExpenseRecord>()
                
                // Process images if any
                if (state.selectedImages.isNotEmpty()) {
                    val imageResult = aiRecordRepository.parseImagesToRecords(state.selectedImages)
                    imageResult.onSuccess { records.addAll(it) }
                        .onFailure { throw it }
                }
                
                // Process text if any input
                if (state.textInput.isNotBlank()) {
                    val textResult = aiRecordRepository.parseTextToRecords(state.textInput)
                    textResult.onSuccess { records.addAll(it) }
                        .onFailure { throw it }
                }
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recognizedRecords = records,
                        errorMessage = if (records.isEmpty()) context.getString(R.string.ai_expense_no_records) else null
                    )
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                android.util.Log.e("AIExpenseViewModel", "Recognition failed: $errorMessage", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = context.getString(R.string.ai_expense_recognition_failed, errorMessage)
                    )
                }
            }
        }
    }
    
    /**
     * Update recognized record at specified index
     */
    fun updateRecord(index: Int, updatedRecord: AIExpenseRecord) {
        _uiState.update { state ->
            val newRecords = state.recognizedRecords.toMutableList()
            if (index in newRecords.indices) {
                newRecords[index] = updatedRecord.copy(isEdited = true)
            }
            state.copy(recognizedRecords = newRecords)
        }
    }
    
    /**
     * Delete record from the UI state
     */
    fun deleteRecord(index: Int) {
        _uiState.update { state ->
            val newRecords = state.recognizedRecords.toMutableList()
            if (index in newRecords.indices) {
                newRecords.removeAt(index)
            }
            state.copy(recognizedRecords = newRecords)
        }
    }
    
    /**
     * Save all valid records
     */
    fun saveAllRecords(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            
            try {
                val validRecords = _uiState.value.recognizedRecords.filter { it.isValid }
                
                if (validRecords.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = context.getString(R.string.ai_expense_no_valid_records)
                        )
                    }
                    return@launch
                }
                
                val result = aiRecordRepository.saveRecords(validRecords)
                
                result.onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            recognizedRecords = emptyList(),
                            selectedImages = emptyList(),
                            textInput = ""
                        )
                    }
                    
                    // Trigger immediate sync after saving records
                    try {
                        syncScheduler.triggerImmediateSync()
                    } catch (e: Exception) {
                        // Sync failure does not affect record saving success
                        android.util.Log.w("AIExpenseViewModel", "Failed to trigger sync after saving AI records", e)
                    }
                    
                    onSuccess()
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = context.getString(R.string.ai_expense_save_failed, error.message ?: "")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = context.getString(R.string.ai_expense_save_failed, e.message ?: "")
                    )
                }
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * Reset state
     */
    fun reset() {
        _uiState.value = AIExpenseUiState()
    }
}

/**
 * AI Expense Record UI State
 */
data class AIExpenseUiState(
    val selectedImages: List<Uri> = emptyList(),
    val textInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val recognizedRecords: List<AIExpenseRecord> = emptyList(),
    val errorMessage: String? = null
)
