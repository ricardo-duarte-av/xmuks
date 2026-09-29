package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.runtime.Immutable
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile

/** Saving, removing, and uploading an avatar for, one set of per-message profiles. */
@Immutable
class PersonaEdits(
    val save: (PerMessageProfile, isDefault: Boolean) -> Unit,
    val delete: (id: String) -> Unit,
    val uploadAvatar: (android.net.Uri, onDone: (mxc: String) -> Unit) -> Unit,
) {
    companion object {
        fun of(actions: PerMessageProfileActions) = PersonaEdits(actions::save, actions::delete, actions::uploadAvatar)
    }
}
