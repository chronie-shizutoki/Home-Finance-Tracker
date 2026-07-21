package com.chronie.homemoney.ui.expense

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.yalantis.ucrop.UCrop
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.graphics.toColorInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AI Expense Screen
 * Displays AI-generated expense records and allows user interaction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIExpenseScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    onRecordsSaved: () -> Unit,
    viewModel: AIExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Crop Image Launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            // Get cropped image URI from uCrop
            val outputUri = UCrop.getOutput(it.data ?: Intent())
            outputUri?.let { uri ->
                viewModel.addImages(listOf(uri))
                // Delete temporary file
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
    
    // Crop Existing Image Launcher
    val existingImageCropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            // Get cropped image URI from uCrop
            val outputUri = UCrop.getOutput(it.data ?: Intent())
            outputUri?.let { uri ->
                viewModel.addImages(listOf(uri))
                // Delete temporary file
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
    
    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) {
        it.forEach { uri ->
                // Start cropping
                try {
                    // Create temporary file to store cropped result
                    val timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                val imageFileName = "CROP_${timeStamp}_"
                val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val image = File(storageDir, "$imageFileName.jpg")
                val outputUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    image
                )
                // Configure uCrop options
                val options = UCrop.Options()
                options.setCompressionQuality(90)
                options.setHideBottomControls(false)
                options.setFreeStyleCropEnabled(true)
                // Set toolbar and status bar colors to avoid overlap
                options.setToolbarColor("#6750A4".toColorInt())
                options.setActiveControlsWidgetColor(android.graphics.Color.WHITE)
                // Ensure crop interface correctly handles status bar space
                options.setToolbarTitle("")
                options.setToolbarWidgetColor(android.graphics.Color.WHITE)
                // Add extra padding to top toolbar to avoid overlapping status bar space
                options.setDimmedLayerColor("#80000000".toColorInt())
                options.setShowCropGrid(false)
                options.setShowCropFrame(true)
                // Start cropping
                val uCrop = UCrop.of(uri, outputUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(1080, 1080)
                    .withOptions(options)
                cropLauncher.launch(uCrop.getIntent(context))
            } catch (e: Exception) {
                Log.e("AIExpenseScreen", "Failed to start crop", e)
            }
        }
    }

    // Camera temporary file URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) {
        if (it) {
            // Capture successful, add image to selection list
            cameraImageUri?.let { uri ->
                // Start cropping
                try {
                    // Create temporary file to store cropped result
                    val timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                    val imageFileName = "CROP_${timeStamp}_"
                    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    val image = File(storageDir, "$imageFileName.jpg")
                    val outputUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        image
                    )
                    // Configure uCrop options
                    val options = UCrop.Options()
                    options.setCompressionQuality(90)
                    options.setHideBottomControls(false)
                    options.setFreeStyleCropEnabled(true)
                    // Set toolbar and status bar colors to avoid overlap
                    options.setToolbarColor("#6750A4".toColorInt())
                    options.setActiveControlsWidgetColor(android.graphics.Color.WHITE)
                    // Ensure crop interface correctly handles status bar space
                    options.setToolbarTitle("")
                    options.setToolbarWidgetColor(android.graphics.Color.WHITE)
                    // Add extra padding to top toolbar to avoid overlapping status bar space
                    options.setDimmedLayerColor("#80000000".toColorInt())
                    options.setShowCropGrid(false)
                    options.setShowCropFrame(true)
                    // Start cropping
                    val uCrop = UCrop.of(uri, outputUri)
                        .withAspectRatio(1f, 1f)
                        .withMaxResultSize(1080, 1080)
                        .withOptions(options)
                    cropLauncher.launch(uCrop.getIntent(context))
                } catch (e: Exception) {
                    Log.e("AIExpenseScreen", "Failed to start crop", e)
                }
            }
        }
    }
    
    // Handle existing image cropping request
    fun handleCropExistingImage(uri: Uri) {
        try {
            // Remove old image from selection list
            viewModel.removeImage(uri)
            // Create temporary file to store cropped result
            val timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            val imageFileName = "CROP_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val image = File(storageDir, "$imageFileName.jpg")
            val outputUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                image
            )
            // Configure uCrop options
            val options = UCrop.Options()
            options.setCompressionQuality(90)
                options.setHideBottomControls(false)
                options.setFreeStyleCropEnabled(true)
                options.setToolbarColor("#6750A4".toColorInt())
                options.setActiveControlsWidgetColor(android.graphics.Color.WHITE)
            // Ensure crop interface correctly handles status bar space
            options.setToolbarTitle("")
            options.setToolbarWidgetColor(android.graphics.Color.WHITE)
            // Add extra padding to top toolbar to avoid overlapping status bar space
            options.setDimmedLayerColor("#80000000".toColorInt())
            options.setShowCropGrid(false)
            options.setShowCropFrame(true)
            // Start cropping
            val uCrop = UCrop.of(uri, outputUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1080, 1080)
                .withOptions(options)
            existingImageCropLauncher.launch(uCrop.getIntent(context))
        } catch (e: Exception) {
            Log.e("AIExpenseScreen", "Failed to start crop", e)
        }
    }

    // Create temporary file for camera capture
    fun createImageFile(context: Context): Uri? {
        val tag = "AIExpenseScreen"
        return try {
            val timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            
            Log.d(tag, "Storage dir: $storageDir")
            
            // Ensure storage directory exists
            if (storageDir?.exists() != true) {
                Log.d(tag, "Creating storage dir: ${storageDir?.mkdirs()}")
            }
            
            // Create file
            val image = File(storageDir, "$imageFileName.jpg")
            
            Log.d(tag, "Image file path: ${image.absolutePath}")
            
            // If file already exists, delete it
            if (image.exists()) {
                Log.d(tag, "Deleting existing file: ${image.delete()}")
            }
            
            // Ensure file is created successfully
            if (image.createNewFile()) {
                Log.d(tag, "File created successfully")
                // Use FileProvider to create URI, avoiding FileUriExposedException
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    image
                )
                Log.d(tag, "Created URI: $uri")
                uri
            } else {
                Log.e(tag, "Failed to create file")
                null
            }
        } catch (ex: IOException) {
            Log.e(tag, "IOException in createImageFile: ${ex.message}", ex)
            null
        } catch (ex: Exception) {
            Log.e(tag, "Exception in createImageFile: ${ex.message}", ex)
            null
        }
    }

    // Camera permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        if (it) {
            // Permission granted, launch camera
            cameraImageUri = createImageFile(context)
            cameraImageUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        }
    }

    // Control display of image source selection dialog box
    var showImageSourceDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.ai_expense_title)) },
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MiuixTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Image selection section
            ImageSelectionSection(
                context = context,
                selectedImages = uiState.selectedImages,
                onAddImages = { showImageSourceDialog = true },
                onRemoveImage = viewModel::removeImage,
                onCropImage = ::handleCropExistingImage
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Text input section
            TextInputSection(
                context = context,
                textInput = uiState.textInput,
                onTextChange = viewModel::updateTextInput
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recognition button
            Button(
                onClick = { viewModel.startRecognition() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && 
                         (uiState.selectedImages.isNotEmpty() || uiState.textInput.isNotBlank())
            ) {
                if (uiState.isLoading) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (uiState.isLoading) 
                        context.getString(R.string.ai_expense_recognizing) 
                    else 
                        context.getString(R.string.ai_expense_start_recognition)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recognized records list
            if (uiState.recognizedRecords.isNotEmpty()) {
                RecognizedRecordsSection(
                    context = context,
                    records = uiState.recognizedRecords,
                    onUpdateRecord = viewModel::updateRecord,
                    onDeleteRecord = viewModel::deleteRecord,
                    onSaveAll = { viewModel.saveAllRecords(onRecordsSaved) },
                    isSaving = uiState.isSaving
                )
            }
            
            // Error prompt
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1
                )
            }
        }
    }

    // Image source selection BottomSheet
    if (showImageSourceDialog) {
        ImageSourceSelectionBottomSheet(
            context = context,
            onDismiss = { showImageSourceDialog = false },
            onCameraSelected = {
                // Check camera permission
                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                
                if (hasCameraPermission) {
                    // If permission granted, launch camera directly
                    cameraImageUri = createImageFile(context)
                    cameraImageUri?.let {
                        cameraLauncher.launch(it)
                    }
                } else {
                    // Request camera permission
                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
                showImageSourceDialog = false
            },
            onGallerySelected = {
                // Launch gallery picker launcher
                imagePickerLauncher.launch("image/*")
                showImageSourceDialog = false
            }
        )
    }
}

/**
 * Image source selection BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSourceSelectionBottomSheet(
    context: Context,
    onDismiss: () -> Unit,
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit
) {
    val bottomSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = context.getString(R.string.ai_expense_select_image_source),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold
            )
            
            // Camera option
            OutlinedButton(
                onClick = onCameraSelected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = context.getString(R.string.ai_expense_take_photo))
                }
            }

            // Gallery option
            OutlinedButton(
                onClick = onGallerySelected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = context.getString(R.string.ai_expense_choose_from_gallery))
                }
            }
        }
    }
}

/**
 * Image selection section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSelectionSection(
    context: Context,
    selectedImages: List<Uri>,
    onAddImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onCropImage: (Uri) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.ai_expense_select_images),
                style = MiuixTheme.textStyles.body1
            )
            TextButton(onClick = onAddImages) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(context.getString(R.string.ai_expense_add_images))
            }
        }
        
        if (selectedImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectedImages.size) { index ->
                    ImagePreviewCard(
                        imageUri = selectedImages[index],
                        onRemove = { onRemoveImage(selectedImages[index]) },
                        onCrop = { onCropImage(selectedImages[index]) }
                    )
                }
            }
        } else {
            Card(
                onClick = onAddImages,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MiuixTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            context.getString(R.string.ai_expense_click_to_add),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Image preview card
 */
@Composable
private fun ImagePreviewCard(
    imageUri: Uri,
    onRemove: () -> Unit,
    onCrop: () -> Unit
) {
    Card(
        modifier = Modifier.size(100.dp)
    ) {
        Box {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        onCrop()
                    },
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Text input section
 */
@Composable
private fun TextInputSection(
    context: Context,
    textInput: String,
    onTextChange: (String) -> Unit
) {
    Column {
        Text(
            text = context.getString(R.string.ai_expense_or_input_text),
            style = MiuixTheme.textStyles.body1
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = { Text(context.getString(R.string.ai_expense_text_hint)) },
            maxLines = 5
        )
    }
}

/**
 * Recognized records section
 */
@Composable
private fun RecognizedRecordsSection(
    context: Context,
    records: List<AIExpenseRecord>,
    onUpdateRecord: (Int, AIExpenseRecord) -> Unit,
    onDeleteRecord: (Int) -> Unit,
    onSaveAll: () -> Unit,
    isSaving: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.ai_expense_records_count, records.size),
                style = MiuixTheme.textStyles.body1
            )
            Button(
                onClick = onSaveAll,
                enabled = !isSaving && records.any { it.isValid }
            ) {
                if (isSaving) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isSaving) 
                        context.getString(R.string.ai_expense_saving) 
                    else 
                        context.getString(R.string.ai_expense_save_all)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(records) { index, record ->
                RecordEditCard(
                    context = context,
                    record = record,
                    onUpdate = { updated -> onUpdateRecord(index, updated) },
                    onDelete = { onDeleteRecord(index) }
                )
            }
        }
    }
}


/**
 * Record edit card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditCard(
    context: Context,
    record: AIExpenseRecord,
    onUpdate: (AIExpenseRecord) -> Unit,
    onDelete: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isValid) 
                MiuixTheme.colorScheme.surface 
            else 
                MiuixTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ExpenseTypeLocalizer.getLocalizedName(context, record.type),
                        style = MiuixTheme.textStyles.body1
                    )
                    Text(
                        text = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), record.amount),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = record.date,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    if (record.remark.isNotBlank()) {
                        Text(
                            text = record.remark,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (record.isEdited) {
                        Text(
                            text = context.getString(R.string.ai_expense_edited),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                Column {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.ai_expense_edit_record))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = context.getString(R.string.ai_expense_delete_record),
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    
    if (showEditDialog) {
        RecordEditDialog(
            context = context,
            record = record,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated ->
                onUpdate(updated)
                showEditDialog = false
            }
        )
    }
}

/**
 * Record edit dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditDialog(
    context: Context,
    record: AIExpenseRecord,
    onDismiss: () -> Unit,
    onConfirm: (AIExpenseRecord) -> Unit
) {
    var selectedType by remember { mutableStateOf(record.type) }
    var amount by remember { mutableStateOf(record.amount.toString()) }
    var remark by remember { mutableStateOf(record.remark) }
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.parse(record.date)) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.ai_expense_edit_record)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type selection
                OutlinedButton(
                    onClick = { showTypePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(ExpenseTypeLocalizer.getLocalizedName(context, selectedType))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                
                // Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(context.getString(R.string.ai_expense_amount)) },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text(context.getString(R.string.currency_symbol)) }
                )
                
                // Date picker
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.DateRange, contentDescription = null)
                }
                
                // Remark input
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(context.getString(R.string.ai_expense_remark)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedRecord = record.copy(
                        type = selectedType,
                        amount = amount.toDoubleOrNull() ?: record.amount,
                        date = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        remark = remark,
                        isEdited = true
                    )
                    onConfirm(updatedRecord)
                }
            ) {
                Text(context.getString(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
    
    if (showTypePicker) {
        ExpenseTypePickerDialog(
            context = context,
            selectedType = selectedType,
            onDismiss = { showTypePicker = false },
            onTypeSelected = { type ->
                selectedType = type
                showTypePicker = false
            }
        )
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDay() * 24 * 60 * 60 * 1000
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = java.time.LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Expense type picker dialog - with search capability
 */
@Composable
private fun ExpenseTypePickerDialog(
    context: Context,
    selectedType: ExpenseType,
    onDismiss: () -> Unit,
    onTypeSelected: (ExpenseType) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter types based on search query
    val filteredTypes = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ExpenseType.entries
        } else {
            ExpenseType.entries.filter { type ->
                val displayName = ExpenseTypeLocalizer.getLocalizedName(context, type)
                displayName.contains(searchQuery, ignoreCase = true) ||
                    type.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(context.getString(R.string.ai_expense_select_type))
                if (filteredTypes.size != ExpenseType.entries.size) {
                    Text(
                        text = context.getString(R.string.search_results_count, filteredTypes.size),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        },
        text = {
            Column {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(context.getString(R.string.search_category)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = context.getString(R.string.clear))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Category list
                if (filteredTypes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.no_results_found),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(filteredTypes.size) { index ->
                            val type = filteredTypes[index]
                            TextButton(
                                onClick = { onTypeSelected(type) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ExpenseTypeLocalizer.getLocalizedName(context, type),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (type == selectedType)
                                        MiuixTheme.colorScheme.primary
                                    else
                                        MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}
