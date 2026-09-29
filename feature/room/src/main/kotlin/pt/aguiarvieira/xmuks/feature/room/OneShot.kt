package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something to tell once (a toast): set, shown, cleared. */
class OneShot<T : Any> {
    private val _value = MutableStateFlow<T?>(null)
    val value: StateFlow<T?> = _value.asStateFlow()

    fun show(value: T) {
        _value.value = value
    }

    fun shown() {
        _value.value = null
    }
}
