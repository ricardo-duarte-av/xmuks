package pt.aguiarvieira.xmuks.core.data.connection

import kotlinx.serialization.json.JsonElement
import pt.aguiarvieira.xmuks.core.network.ExecResult
import java.io.IOException

/** A command's outcome as a [Result]: its data, or why it failed (gomuks' message, or the network's). */
internal fun ExecResult.toResult(): Result<JsonElement> =
    when (this) {
        is ExecResult.Ok -> Result.success(data)
        is ExecResult.CommandError -> Result.failure(IOException(message))
        is ExecResult.NetworkError -> Result.failure(cause)
    }
