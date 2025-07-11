package kpm.core

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> = apply {
        if (this is Success) action(data)
    }

    inline fun onError(action: (String, Throwable?) -> Unit): Result<T> = apply {
        if (this is Error) action(message, cause)
    }

}
