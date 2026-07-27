package eu.kanade.tachiyomi.source.model

sealed class Filter<T>(val name: String, var state: T)