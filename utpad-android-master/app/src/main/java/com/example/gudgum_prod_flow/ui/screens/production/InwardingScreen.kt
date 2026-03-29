package com.example.gudgum_prod_flow.ui.screens.production

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.example.gudgum_prod_flow.ui.components.BarcodeScannerButton
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import androidx.compose.material3.TopAppBarDefaults
import com.example.gudgum_prod_flow.ui.components.SearchableDropdown
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.viewmodels.InwardingViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import com.example.gudgum_prod_flow.ui.viewmodels.Vendor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InwardingScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: InwardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val selectedIngredient by viewModel.selectedIngredient.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val expiryDate by viewModel.expiryDate.collectAsState()
    val selectedVendor by viewModel.selectedVendor.collectAsState()
    val billNumber by viewModel.billNumber.collectAsState()
    val billPhotoUri by viewModel.billPhotoUri.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()
    val availableIngredients by viewModel.availableIngredients.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val addIngredientState by viewModel.addIngredientState.collectAsState()
    val addVendorState by viewModel.addVendorState.collectAsState()

    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var showAddVendorDialog by remember { mutableStateOf(false) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraPhotoUri != null) viewModel.setBillPhotoUri(cameraPhotoUri.toString())
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setBillPhotoUri(it.toString()) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is SubmitState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearSubmitState()
            }
            is SubmitState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearSubmitState()
            }
            else -> Unit
        }
    }
    LaunchedEffect(addIngredientState) {
        when (val state = addIngredientState) {
            is SubmitState.Success -> { snackbarHostState.showSnackbar(state.message); viewModel.clearAddIngredientState() }
            is SubmitState.Error -> { snackbarHostState.showSnackbar(state.message); viewModel.clearAddIngredientState() }
            else -> Unit
        }
    }
    LaunchedEffect(addVendorState) {
        when (val state = addVendorState) {
            is SubmitState.Success -> { snackbarHostState.showSnackbar(state.message); viewModel.clearAddVendorState() }
            is SubmitState.Error -> { snackbarHostState.showSnackbar(state.message); viewModel.clearAddVendorState() }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Inwarding Wizard", fontWeight = FontWeight.Bold, color = UtpadTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = UtpadTextPrimary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = UtpadPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UtpadBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                OperationsModuleTabs(
                    currentRoute = AppRoute.Inwarding,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                WizardProgressBar(
                    currentStep = currentStep,
                    totalSteps = 3,
                    stepTitle = when(currentStep) {
                        1 -> "Vendor & Ingredient"
                        2 -> "Batch Details"
                        else -> "Billing & Photo"
                    }
                )

                when (currentStep) {
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Column {
                                    Text(
                                        text = "SOURCE DETAILS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        SearchableDropdown(
                                            items = availableIngredients,
                                            selectedItem = selectedIngredient,
                                            onItemSelected = { viewModel.onIngredientSelected(it) },
                                            itemLabel = { it.name },
                                            placeholder = "Choose ingredient",
                                            onAddNewClick = { showAddIngredientDialog = true },
                                            addNewLabel = "Add New Ingredient",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = "MATERIAL DETAILS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SearchableDropdown(
                                        items = vendors,
                                        selectedItem = selectedVendor,
                                        onItemSelected = { viewModel.onVendorSelected(it) },
                                        itemLabel = { it.name },
                                        placeholder = "Select vendor...",
                                        onAddNewClick = { showAddVendorDialog = true },
                                        addNewLabel = "Add New Vendor",
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {


                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "QUANTITY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = quantity,
                                    onValueChange = viewModel::onQuantityChanged,
                                    placeholder = { Text("0.00", color = UtpadTextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = UtpadPrimary,
                                        unfocusedBorderColor = UtpadOutline,
                                        focusedContainerColor = UtpadBackground,
                                        unfocusedContainerColor = UtpadSurface,
                                        focusedTextColor = UtpadTextPrimary,
                                        unfocusedTextColor = UtpadTextPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "UNIT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                var unitExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = unitExpanded,
                                    onExpandedChange = { unitExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = selectedUnit,
                                        onValueChange = {},
                                        readOnly = true,
                                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UtpadPrimary,
                                            unfocusedBorderColor = UtpadOutline,
                                            focusedContainerColor = UtpadBackground,
                                            unfocusedContainerColor = UtpadSurface,
                                            focusedTextColor = UtpadTextPrimary,
                                            unfocusedTextColor = UtpadTextPrimary
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth(),
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = unitExpanded,
                                        onDismissRequest = { unitExpanded = false },
                                    ) {
                                        viewModel.units.forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit) },
                                                onClick = {
                                                    viewModel.onUnitSelected(unit)
                                                    unitExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column {
                            Text(
                                text = "EXPIRY DATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var showDatePicker by remember { mutableStateOf(false) }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = expiryDate,
                                    onValueChange = viewModel::onExpiryDateChanged,
                                    placeholder = { Text("dd-mm-yyyy", color = UtpadTextSecondary) },
                                    singleLine = true,
                                    readOnly = true,
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = UtpadPrimary,
                                        unfocusedBorderColor = UtpadOutline,
                                        focusedContainerColor = UtpadBackground,
                                        unfocusedContainerColor = UtpadSurface,
                                        focusedTextColor = UtpadTextPrimary,
                                        unfocusedTextColor = UtpadTextPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.PhotoCamera, // Using a generic icon but ideally calendar icon
                                            contentDescription = "Pick Date",
                                            tint = UtpadTextPrimary
                                        )
                                    }
                                )
                                // Invisible surface to capture clicks
                                Surface(
                                    modifier = Modifier.matchParentSize(),
                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                    onClick = { showDatePicker = true }
                                ) {}
                            }

                            if (showDatePicker) {
                                val datePickerState = rememberDatePickerState()
                                DatePickerDialog(
                                    onDismissRequest = { showDatePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val selectedMillis = datePickerState.selectedDateMillis
                                            if (selectedMillis != null) {
                                                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                viewModel.onExpiryDateChanged(formatter.format(Date(selectedMillis)))
                                            }
                                            showDatePicker = false
                                        }) {
                                            Text("OK")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePicker = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column {
                            Text(
                                text = "BILL NUMBER",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = billNumber,
                                onValueChange = viewModel::onBillNumberChanged,
                                placeholder = { Text("e.g. INV-2023-001", color = UtpadTextSecondary) },
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = UtpadPrimary,
                                    unfocusedBorderColor = UtpadOutline,
                                    focusedContainerColor = UtpadBackground,
                                    unfocusedContainerColor = UtpadSurface,
                                    focusedTextColor = UtpadTextPrimary,
                                    unfocusedTextColor = UtpadTextPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                trailingIcon = {
                                    BarcodeScannerButton(
                                        prompt = "Scan bill / invoice barcode",
                                        onBarcodeScanned = { viewModel.onBillNumberChanged(it) },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadBackground),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = UtpadPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                if (billPhotoUri != null) {
                                    Text(
                                        text = "Photo captured",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = UtpadSuccess,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    TextButton(onClick = { viewModel.setBillPhotoUri(null) }) {
                                        Text("Remove / Retake", color = UtpadTextSecondary)
                                    }
                                } else {
                                    Text(
                                        text = "Bill Photo",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = UtpadTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Button(
                                            onClick = {
                                                val imageFile = File(context.cacheDir, "bill_${System.currentTimeMillis()}.jpg")
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
                                                cameraPhotoUri = uri
                                                cameraLauncher.launch(uri)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = UtpadPrimary, contentColor = Color.White),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Camera")
                                        }
                                        OutlinedButton(
                                            onClick = { galleryLauncher.launch("image/*") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Text("Gallery")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 20.dp,
                color = UtpadSurface,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedButton(
                            onClick = { 
                                if (currentStep > 1) {
                                    viewModel.previousStep()
                                } else {
                                    viewModel.reset()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = UtpadTextPrimary
                            )
                        ) {
                            if (currentStep > 1) {
                                Text("Back", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Reset", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (currentStep < 3) {
                                    viewModel.nextStep()
                                } else {
                                    viewModel.submit()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = UtpadPrimary,
                                contentColor = Color.White
                            ),
                            enabled = submitState !is SubmitState.Loading,
                        ) {
                            if (currentStep < 3) {
                                Text("Continue", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Confirm Inward", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (AppRoute.Production in allowedRoutes) {
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Production) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue to Production")
                        }
                    }
                }
            }
        }
    }

    if (showAddIngredientDialog) {
        AddIngredientDialog(
            onDismiss = { showAddIngredientDialog = false },
            onConfirm = { name, unit, vendorName ->
                showAddIngredientDialog = false
                viewModel.addIngredient(name, unit, vendorName)
            },
            isSaving = addIngredientState is SubmitState.Loading,
            units = viewModel.units,
            vendors = vendors,
        )
    }
    if (showAddVendorDialog) {
        AddVendorDialog(
            onDismiss = { showAddVendorDialog = false },
            onConfirm = { name, contact ->
                showAddVendorDialog = false
                viewModel.addVendor(name, contact)
            },
            isSaving = addVendorState is SubmitState.Loading,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIngredientDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String, vendorName: String) -> Unit,
    isSaving: Boolean,
    units: List<String>,
    vendors: List<Vendor>,
) {
    var name by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(units.firstOrNull() ?: "kg") }
    var unitExpanded by remember { mutableStateOf(false) }
    var vendorName by remember { mutableStateOf("") }
    var vendorExpanded by remember { mutableStateOf(false) }
    val filteredVendors = vendors.filter { it.name.contains(vendorName, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Ingredient", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ingredient Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedUnit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false },
                    ) {
                        units.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = { selectedUnit = unit; unitExpanded = false },
                            )
                        }
                    }
                }
                // Vendor field — type to filter existing, or enter a new name
                ExposedDropdownMenuBox(
                    expanded = vendorExpanded && filteredVendors.isNotEmpty(),
                    onExpandedChange = { vendorExpanded = it },
                ) {
                    OutlinedTextField(
                        value = vendorName,
                        onValueChange = { vendorName = it; vendorExpanded = true },
                        label = { Text("Vendor / Supplier *") },
                        placeholder = { Text("Type name or pick from list") },
                        singleLine = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    if (filteredVendors.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = vendorExpanded,
                            onDismissRequest = { vendorExpanded = false },
                        ) {
                            filteredVendors.forEach { vendor ->
                                DropdownMenuItem(
                                    text = { Text(vendor.name) },
                                    onClick = { vendorName = vendor.name; vendorExpanded = false },
                                )
                            }
                        }
                    }
                }
                if (vendorName.isNotBlank() && vendors.none { it.name.equals(vendorName, ignoreCase = true) }) {
                    Text(
                        text = "\"$vendorName\" will be created as a new vendor",
                        style = MaterialTheme.typography.bodySmall,
                        color = UtpadTextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && vendorName.isNotBlank()) onConfirm(name, selectedUnit, vendorName)
                },
                enabled = name.isNotBlank() && vendorName.isNotBlank() && !isSaving,
            ) { Text(if (isSaving) "Saving..." else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddVendorDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, contact: String?) -> Unit,
    isSaving: Boolean,
) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Vendor", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vendor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text("Contact (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, contact.ifBlank { null }) },
                enabled = name.isNotBlank() && !isSaving,
            ) { Text(if (isSaving) "Saving..." else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = UtpadTextSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun WizardProgressBar(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String,
    modifier: Modifier = Modifier
) {
    val progress = currentStep.toFloat() / totalSteps.toFloat()
    val percentage = (progress * 100).toInt()
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "STEP $currentStep OF $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = UtpadTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Progress Bar Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(UtpadOutline, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(6.dp)
                    .background(UtpadPrimary, RoundedCornerShape(3.dp))
            )
        }
    }
}
