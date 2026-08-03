package eu.kanade.tachiyomi.source.model

import android.net.Uri

/**
 * Compile-only stub. Call sites must pass all four args (no Kotlin defaults)
 * so the bytecode matches the host `Page(Int, String, String?, Uri?)` ctor.
 */
class Page(
    val index: Int,
    val url: String,
    var imageUrl: String?,
    var uri: Uri?,
)
