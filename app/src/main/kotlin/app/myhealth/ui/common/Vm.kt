package app.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Manual-DI ViewModel creation: the lambda receives dependencies explicitly, no reflection. */
@Composable
inline fun <reified VM : ViewModel> rememberVm(key: String? = null, noinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })
