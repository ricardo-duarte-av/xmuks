package pt.aguiarvieira.xmuks.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import javax.inject.Inject

/** This device's push gateway: gomuks' default, or one of its own (which then registers at once). */
@HiltViewModel
class PushGatewayViewModel
    @Inject
    constructor(
        private val push: PushRegistrar,
    ) : ViewModel() {
        /** The custom gateway's host; null for the default. */
        val gateway: StateFlow<String?> =
            push.gateway.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null
            )

        fun set(input: String?) {
            viewModelScope.launch { push.setGateway(input) }
        }
    }
