package com.mezberg.sleepwave.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.data.SleepPeriodEntity
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.SleepPeriodDisplayData
import com.mezberg.sleepwave.viewmodel.SleepTrackingUiState
import com.mezberg.sleepwave.viewmodel.SleepTrackingViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Data class to hold the state of the add dialog
data class AddSleepPeriodState(
    val startDate: Date? = null,
    val startTime: String = "",
    val endDate: Date? = null,
    val endTime: String = ""
)

@Composable
fun SleepTrackingScreen(
    uiState: SleepTrackingUiState,
    onPermissionRequest: () -> Unit,
    onDeleteSleepPeriod: (SleepPeriodEntity) -> Unit,
    onAddSleepPeriod: suspend (Date, String, Date, String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sleep_tracking),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            if (!uiState.hasPermission) {
                Button(
                    onClick = onPermissionRequest,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(text = stringResource(R.string.grant_usage_access))
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (!uiState.isLoading && uiState.error == null) {
                SleepPeriodsList(
                    sleepPeriods = uiState.sleepPeriods,
                    onDeleteSleepPeriod = onDeleteSleepPeriod,
                    onAddSleepPeriod = onAddSleepPeriod,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SleepPeriodsList(
    sleepPeriods: List<SleepPeriodDisplayData>,
    onDeleteSleepPeriod: (SleepPeriodEntity) -> Unit,
    onAddSleepPeriod: suspend (Date, String, Date, String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    if (sleepPeriods.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_sleep_periods),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sleepPeriods) { dayData ->
                DaySleepCard(
                    dayData = dayData,
                    onDeleteSleepPeriod = onDeleteSleepPeriod,
                    onAddSleepPeriod = onAddSleepPeriod
                )
            }
        }
    }
}

@Composable
private fun AddSleepPeriodDialog(
    onDismiss: () -> Unit,
    onConfirm: (startDate: Date, startTime: String, endDate: Date, endTime: String) -> Unit
) {
    var state by remember { mutableStateOf(AddSleepPeriodState()) }
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // Date Picker Dialog
    fun showDatePicker(isStartDate: Boolean) {
        val initialCalendar = Calendar.getInstance()
        state.startDate?.let { initialCalendar.time = it }

        DatePickerDialog(
            context,
            R.style.Theme_SleepWave_DatePicker,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                if (isStartDate) {
                    state = state.copy(startDate = calendar.time)
                } else {
                    state = state.copy(endDate = calendar.time)
                }
            },
            initialCalendar.get(Calendar.YEAR),
            initialCalendar.get(Calendar.MONTH),
            initialCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Time Picker Dialog
    fun showTimePicker(isStartTime: Boolean) {
        TimePickerDialog(
            context,
            R.style.Theme_SleepWave_TimePicker,
            { _, hourOfDay, minute ->
                val time = String.format("%02d:%02d", hourOfDay, minute)
                if (isStartTime) {
                    state = state.copy(startTime = time)
                } else {
                    state = state.copy(endTime = time)
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24-hour format
        ).show()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Sleep Period") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start Date
                OutlinedTextField(
                    value = state.startDate?.let { 
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it)
                    } ?: "Select date",
                    onValueChange = { },
                    label = { Text("Start Date") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker(true) }) {
                            Text("Edit")
                        }
                    }
                )

                // Start Time
                OutlinedTextField(
                    value = state.startTime.ifEmpty { "Select time" },
                    onValueChange = { },
                    label = { Text("Start Time") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showTimePicker(true) }) {
                            Text("Edit")
                        }
                    }
                )

                // End Date
                OutlinedTextField(
                    value = state.endDate?.let { 
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it)
                    } ?: "Select date",
                    onValueChange = { },
                    label = { Text("End Date") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker(false) }) {
                            Text("Edit")
                        }
                    }
                )

                // End Time
                OutlinedTextField(
                    value = state.endTime.ifEmpty { "Select time" },
                    onValueChange = { },
                    label = { Text("End Time") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showTimePicker(false) }) {
                            Text("Edit")
                        }
                    }
                )
            }
        },
        confirmButton = {
            val startDate = state.startDate
            val endDate = state.endDate
            val startTime = state.startTime
            val endTime = state.endTime
            
            Button(
                onClick = {
                    if (startDate != null && startTime.isNotEmpty() &&
                        endDate != null && endTime.isNotEmpty()) {
                        onConfirm(startDate, startTime, endDate, endTime)
                    }
                },
                enabled = startDate != null && startTime.isNotEmpty() &&
                         endDate != null && endTime.isNotEmpty()
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DaySleepCard(
    dayData: SleepPeriodDisplayData,
    onDeleteSleepPeriod: (SleepPeriodEntity) -> Unit,
    onAddSleepPeriod: suspend (Date, String, Date, String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var isEditMode by remember { mutableStateOf(false) }
    var sleepPeriodToDelete by remember { mutableStateOf<SleepPeriodEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    // Error Dialog
    showErrorDialog?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Add Sleep Period Dialog
    if (showAddDialog) {
        AddSleepPeriodDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { startDate, startTime, endDate, endTime ->
                // Launch in a coroutine
                MainScope().launch {
                    val result = onAddSleepPeriod(startDate, startTime, endDate, endTime)
                    result.fold(
                        onSuccess = {
                            showAddDialog = false
                        },
                        onFailure = { error ->
                            val errorMessage = when (error) {
                                is SleepTrackingViewModel.AddSleepPeriodError.Overlap ->
                                    error.message
                                is SleepTrackingViewModel.AddSleepPeriodError.TooLong ->
                                    error.message
                                is IllegalArgumentException ->
                                    error.message ?: "Invalid time range"
                                else -> error.message ?: "Failed to add sleep period"
                            }
                            showErrorDialog = errorMessage
                        }
                    )
                }
            }
        )
    }

    // Confirmation Dialog
    sleepPeriodToDelete?.let { period ->
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { sleepPeriodToDelete = null },
            title = { Text("Confirm Deletion") },
            text = { 
                Text(
                    "Do you want to delete sleep period on \n" +
                    "${dateFormat.format(period.start)} from ${timeFormat.format(period.start)} to ${timeFormat.format(period.end)}?"
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSleepPeriod(period)
                        sleepPeriodToDelete = null
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { sleepPeriodToDelete = null }) {
                    Text("No")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayData.date,
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(
                    onClick = { isEditMode = !isEditMode },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (isEditMode) "Exit edit mode" else "Enter edit mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            dayData.periods.forEach { period ->
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dateFormat.format(period.start)} - ${dateFormat.format(period.end)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    
                    if (isEditMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { sleepPeriodToDelete = period },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete sleep period",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total sleep: ${dayData.totalSleepHours}h ${dayData.totalSleepMinutes}m",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (isEditMode) {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add sleep period",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SleepTrackingScreenPreview() {
    SleepWaveTheme {
        SleepTrackingScreen(
            uiState = SleepTrackingUiState(
                sleepPeriods = listOf(
                    SleepPeriodDisplayData(
                        date = "Mar 15, 2024",
                        periods = emptyList(),
                        totalSleepHours = 7,
                        totalSleepMinutes = 30
                    )
                )
            ),
            onPermissionRequest = {},
            onDeleteSleepPeriod = {},
            onAddSleepPeriod = { _, _, _, _ -> Result.success(Unit) }
        )
    }
} 