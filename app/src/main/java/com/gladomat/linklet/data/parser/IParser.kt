package com.gladomat.linklet.data.parser

import com.gladomat.linklet.data.model.Note

/**
 * Parses raw note content into a [Note].
 */
interface IParser {
    fun parse(content: String, path: String): Note
}
