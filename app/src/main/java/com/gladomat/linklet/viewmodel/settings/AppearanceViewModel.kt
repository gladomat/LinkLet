package com.gladomat.linklet.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.settings.AppearanceSettingsRepository
import com.gladomat.linklet.data.settings.ThemeId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Supplies the active theme to `MainActivity`, which owns `LinkLetAppTheme` and therefore sits
 * above every screen-scoped ViewModel.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    appearanceSettingsRepository: AppearanceSettingsRepository,
) : ViewModel() {

    val themeId: StateFlow<ThemeId> = appearanceSettingsRepository.themeIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeId.AMBERLINK)
}
