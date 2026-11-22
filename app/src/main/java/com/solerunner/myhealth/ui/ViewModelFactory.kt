package com.solerunner.myhealth.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solerunner.myhealth.di.AppContainer

/**
 * Creates a ViewModel with constructor injection from [AppContainer] without a DI framework.
 *
 * Usage: `val vm = containerViewModel(container) { DashboardViewModel(it) }`.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory { initializer { create(container) } },
)
