package com.example.aichat.feature.chat

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Early background URLs exposed the R2 key as several literal path segments,
 * even though the asset endpoint accepts the key as one segment. Convert both
 * old and current URLs to the canonical encoded form before Coil requests it.
 */
internal fun canonicalChatBackgroundUrl(value: String?): String? {
    val url = value?.trim()?.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" && url.scheme != "http") return null

    val segments = url.pathSegments
    val assetsIndex = segments.indexOf("assets")
    if (assetsIndex < 0 || assetsIndex + 1 >= segments.size) return url.toString()

    val prefix = segments.take(assetsIndex + 1)
    val assetKey = segments.drop(assetsIndex + 1).joinToString("/")
    if (assetKey.isBlank()) return null

    return url.newBuilder()
        .query(null)
        .fragment(null)
        .encodedPath("/")
        .apply {
            prefix.forEach(::addPathSegment)
            addPathSegment(assetKey)
        }
        .build()
        .toString()
}
