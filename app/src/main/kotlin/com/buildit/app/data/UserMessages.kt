package com.buildit.app.data

import com.buildit.core.model.EditException
import com.buildit.core.model.InvalidProjectException
import com.buildit.core.model.ProjectFormatException
import com.buildit.core.storage.StoreException
import java.io.IOException

/** Converts failures into short, user-facing text. Never includes stack traces or paths. */
fun Throwable.toUserMessage(): String = when (this) {
    is EditException -> message ?: "That change is not allowed"
    is InvalidProjectException -> message ?: "The project is not valid"
    is StoreException.NotFound -> "That project no longer exists"
    is StoreException.Conflict -> "The project changed in the meantime. Reloaded the latest version."
    is StoreException.Corrupt -> "The project file is damaged. Open it to recover from history."
    is StoreException.InvalidArchive -> message ?: "Not a valid project archive"
    is ProjectFormatException -> message ?: "The project file is not readable"
    is IOException -> "Storage error: ${message ?: "unknown"}"
    else -> "Something went wrong (${this::class.simpleName})"
}
