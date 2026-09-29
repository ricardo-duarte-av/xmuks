package pt.aguiarvieira.xmuks.feature.profile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository

/** Changes on their way to the server: whether one is running, and the last one that failed. */
class ProfileTasks(
    private val scope: CoroutineScope,
) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** For a snackbar; cleared once shown. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun run(
        block: suspend () -> Result<*>,
        then: suspend () -> Unit = {},
    ) {
        scope.launch {
            _busy.value = true
            block().onFailure { _error.value = it.message ?: it.javaClass.simpleName }
            then()
            _busy.value = false
        }
    }

    fun errorShown() {
        _error.value = null
    }
}

/** Reads a picked image and uploads it; the result is its `mxc://` URI. */
class ImageUploader(
    private val context: Context,
    private val profiles: ProfileRepository,
) {
    suspend fun upload(uri: Uri): Result<String> {
        val file =
            withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't read the image")
                    val name =
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        } ?: "image"
                    Triple(name, resolver.getType(uri), bytes)
                }
            }
        return file.fold(
            onSuccess = { (name, type, bytes) -> profiles.upload(name, type, bytes) },
            onFailure = { Result.failure(it) },
        )
    }
}

/** Adding, editing and removing our per-message profiles (MSC4461). */
class PerMessageProfileActions(
    private val profiles: ProfileRepository,
    private val uploader: ImageUploader,
    private val tasks: ProfileTasks,
) {
    /** Uploads a profile's new avatar; [onDone] gets its `mxc://` URI to put in the draft. */
    fun uploadAvatar(
        uri: Uri,
        onDone: (String) -> Unit,
    ) = tasks.run({ uploader.upload(uri).onSuccess(onDone) })

    fun save(
        profile: PerMessageProfile,
        isDefault: Boolean,
    ) = edit { current ->
        val updated = current.upsert(profile)
        when {
            isDefault -> updated.copy(defaultId = profile.id)
            updated.defaultId == profile.id -> updated.copy(defaultId = null)
            else -> updated
        }
    }

    fun delete(id: String) = edit { it.remove(id) }

    private fun edit(change: (PerMessageProfiles) -> PerMessageProfiles) =
        tasks.run({ profiles.savePerMessageProfiles(change(profiles.perMessageProfiles.first())) })
}
