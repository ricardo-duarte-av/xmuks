package pt.aguiarvieira.xmuks.feature.room

/** Resolves `mxc://` for the timeline: avatars as thumbnails, media as full files (decrypted by gomuks). */
class MediaResolver(
    val avatar: (String?) -> String?,
    val media: (mxc: String, encrypted: Boolean) -> String?,
)
