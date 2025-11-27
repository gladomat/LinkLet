package com.gladomat.linklet.data.sync

import java.io.IOException

/**
 * Thrown when a remote operation fails because the resource has changed on the server
 * (HTTP 412 Precondition Failed).
 */
class RemoteChangedException(message: String) : IOException(message)
