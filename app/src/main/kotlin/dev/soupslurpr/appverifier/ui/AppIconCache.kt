package dev.soupslurpr.appverifier.ui

import android.graphics.drawable.Drawable
import android.util.LruCache

object AppIconCache {
    private const val MAX_SIZE = 256

    private val cache = LruCache<String, Drawable>(MAX_SIZE)

    fun get(packageName: String): Drawable? = cache.get(packageName)

    fun put(packageName: String, icon: Drawable) {
        cache.put(packageName, icon)
    }
}
