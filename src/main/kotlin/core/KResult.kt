/*
Copyright 2025 Lucas HEINTZMANN

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package kpm.core

sealed class KResult<out T> {
    data class Success<out T>(val data: T) : KResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : KResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): KResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): KResult<T> = apply {
        if (this is Success) action(data)
    }

    inline fun onError(action: (String, Throwable?) -> Unit): KResult<T> = apply {
        if (this is Error) action(message, cause)
    }

}
