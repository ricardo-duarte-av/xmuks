package pt.aguiarvieira.xmuks.feature.settings

import androidx.compose.runtime.Immutable
import pt.aguiarvieira.xmuks.core.data.prefs.Pref
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope

/** Changing preferences in one scope: set a value there, or clear it (the next scope's applies). */
@Immutable
class PrefEdits(
    val setBool: (Pref.Bool, PrefScope, Boolean) -> Unit = { _, _, _ -> },
    val setNumber: (Pref.Number, PrefScope, Int) -> Unit = { _, _, _ -> },
    val setChoice: (Pref.Choice, PrefScope, String?) -> Unit = { _, _, _ -> },
    val clear: (Pref<*>, PrefScope) -> Unit = { _, _ -> },
)
