package com.mezberg.sleepwave.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mezberg.sleepwave.viewmodel.OnboardViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun OnboardingScreen(
    viewModel: OnboardViewModel,
    onOnboardingComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.shouldNavigateToMain) {
        if (uiState.shouldNavigateToMain) {
            onOnboardingComplete()
            viewModel.onNavigatedToMain()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Progress indicator with surface background
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = uiState.currentStep.toFloat() / (uiState.totalSteps - 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.background),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = uiState.currentStepContent,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (uiState.currentStep > 0) {
                Button(onClick = { viewModel.previousStep() }) {
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(88.dp)) // Width of a typical button
            }

            if (uiState.currentStep < uiState.totalSteps - 1) {
                Button(onClick = { viewModel.nextStep() }) {
                    Text("Next")
                }
            } else {
                Button(
                    onClick = { viewModel.completeOnboarding() }
                ) {
                    Text("Get Started")
                }
            }
        }
    }
} 