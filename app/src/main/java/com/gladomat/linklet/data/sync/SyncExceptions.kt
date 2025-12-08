package com.gladomat.linklet.data.sync

// Custom exception for when sync tries to delete all remote files
class CatastrophicDeleteException(message: String) : RuntimeException(message)

// Custom exception for when sync wants to delete a large number of files, requiring user confirmation
class RequiresConfirmationException(val pendingDeletesCount: Int, message: String) : RuntimeException(message)

// Custom exception for when local storage appears misconfigured (empty but we have sync states)
class LocalStorageMisconfiguredException(message: String) : RuntimeException(message)
