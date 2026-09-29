package pt.aguiarvieira.xmuks.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.prefs.Pref
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore

/** The preferences of one room's scopes ([roomId]), or the global ones (null). */
@HiltViewModel(assistedFactory = PreferencesViewModel.Factory::class)
class PreferencesViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String?,
        private val store: PreferenceStore,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String?): PreferencesViewModel
        }

        val layers: StateFlow<PrefLayers?> =
            store
                .layers(
                    roomId
                ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Why the last change failed; cleared once shown. */
        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        fun <T> set(
            pref: Pref<T>,
            scope: PrefScope,
            value: T,
        ) = run { store.set(pref, scope, value, roomId) }

        fun clear(
            pref: Pref<*>,
            scope: PrefScope,
        ) = run { store.clear(pref, scope, roomId) }

        fun errorShown() {
            _error.value = null
        }

        private fun run(block: suspend () -> Result<Unit>) {
            viewModelScope.launch { block().onFailure { _error.value = it.message ?: it.javaClass.simpleName } }
        }
    }
