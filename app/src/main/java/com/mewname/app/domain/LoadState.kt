package com.mewname.app.domain

/** Represents every visible outcome of an asynchronous data load. */
sealed interface LoadState<out T> {
    object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    object Empty : LoadState<Nothing>
    data class Error(val cause: Throwable) : LoadState<Nothing>
}