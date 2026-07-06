package com.gladomat.linklet.data.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Utility functions for manipulating Org-mode files as plain text.
 *
 * All drawer manipulation occurs directly inside the text content—no separate
 * metadata database or JSON structure. This ensures compatibility with:
 * - Emacs Org-mode
 * - org-roam / org-id
 * - External editors (Orgzly, beorg, Logseq)
 */
object OrgFileUtils {

    private const val MAX_TITLE_LENGTH = 60
    private const val TIMESTAMP_PATTERN = "yyyyMMddHHmmss"

    private val TITLE_REGEX = Regex(
        pattern = """^#\+title:[ \t]*(.*)$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    private val PROPERTIES_DRAWER_REGEX = Regex(
        pattern = """(?s)^:PROPERTIES:\s*\n(.*?):END:""",
        options = setOf(RegexOption.MULTILINE),
    )

    private val ID_PROPERTY_REGEX = Regex(
        pattern = """^:ID:\s*(.*)$""",
        options = setOf(RegexOption.MULTILINE),
    )

    /**
     * Extracts the title from an Org file's `#+title:` line.
     * @return The trimmed title, or "untitled" if empty or missing.
     */
    fun extractTitle(content: String): String {
        val match = TITLE_REGEX.find(content)
        val rawTitle = match?.groupValues?.getOrNull(1)?.trim()
        return rawTitle?.takeIf { it.isNotEmpty() } ?: "untitled"
    }

    /**
     * Truncates a title for UI display, appending "…" if truncated.
     */
    fun truncateForUi(title: String): String {
        return if (title.length > MAX_TITLE_LENGTH) {
            title.take(MAX_TITLE_LENGTH) + "…"
        } else {
            title
        }
    }

    /**
     * Truncates a title for filename use (no ellipsis).
     */
    fun truncateForFilename(title: String): String {
        return title.take(MAX_TITLE_LENGTH)
    }

    /**
     * Slugifies a title for use in filenames.
     *
     * Rules:
     * - Lowercase
     * - Replace non-alphanumeric with "_"
     * - Collapse repeated "_"
     * - Trim leading/trailing "_"
     * - Return "untitled" if result is empty
     */
    fun slugifyTitle(title: String): String {
        val truncated = truncateForFilename(title)
        val slug = truncated
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return slug.ifEmpty { "untitled" }
    }

    /**
     * Generates a unique note ID in org-id UUID format.
     *
     * Returns uppercase UUID text with dashes, e.g.
     * `09F227F6-01ED-4B3D-9D0C-5EA03961E109`.
     */
    @Suppress("UNUSED_PARAMETER")
    fun generateNoteId(title: String, createdAt: LocalDateTime): String {
        return UUID.randomUUID().toString().uppercase(Locale.ROOT)
    }

    /**
     * Generates a filename for a new note.
     *
     * Format: `yyyyMMddHHmmss-<slug>.org`
     */
    fun generateFilename(title: String, createdAt: LocalDateTime): String {
        val timestamp = createdAt.format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN))
        val slug = slugifyTitle(title)
        return "$timestamp-$slug.org"
    }

    /**
     * Ensures the :ID: property exists in the PROPERTIES drawer.
     *
     * Behavior:
     * 1. If :PROPERTIES: drawer exists:
     *    - If :ID: exists with empty value → replace with given ID
     *    - If :ID: exists with non-empty value → keep existing (never overwrite)
     *    - If no :ID: → insert after :PROPERTIES:
     * 2. If no drawer exists:
     *    - Prepend a full PROPERTIES drawer block at the start of the file
     *
     * All manipulation is plain text to maintain Org-mode compatibility.
     *
     * @param content The current file content
     * @param id The ID to insert (only used if no existing ID)
     * @return The updated content with ID in drawer
     */
    fun ensureIdInProperties(content: String, id: String): String {
        val drawerMatch = PROPERTIES_DRAWER_REGEX.find(content)

        return if (drawerMatch != null) {
            insertIdIntoExistingDrawer(content, drawerMatch, id)
        } else {
            prependNewDrawer(content, id)
        }
    }

    /**
     * Checks if the content already has a non-empty :ID: property.
     */
    fun hasExistingId(content: String): Boolean {
        val drawerMatch = PROPERTIES_DRAWER_REGEX.find(content) ?: return false
        val drawerContent = drawerMatch.groupValues.getOrNull(1) ?: return false
        val idMatch = ID_PROPERTY_REGEX.find(drawerContent)
        val existingId = idMatch?.groupValues?.getOrNull(1)?.trim()
        return !existingId.isNullOrEmpty()
    }

    private fun insertIdIntoExistingDrawer(
        content: String,
        drawerMatch: MatchResult,
        id: String,
    ): String {
        val drawerContent = drawerMatch.groupValues.getOrNull(1) ?: ""
        val idMatch = ID_PROPERTY_REGEX.find(drawerContent)

        return if (idMatch != null) {
            val existingId = idMatch.groupValues.getOrNull(1)?.trim()
            if (existingId.isNullOrEmpty()) {
                // Replace empty :ID: with the new ID
                val newDrawerContent = drawerContent.replaceFirst(
                    ID_PROPERTY_REGEX,
                    ":ID:       $id",
                )
                content.replaceFirst(
                    PROPERTIES_DRAWER_REGEX,
                    ":PROPERTIES:\n$newDrawerContent:END:",
                )
            } else {
                // Keep existing non-empty ID unchanged
                content
            }
        } else {
            // No :ID: property, insert after :PROPERTIES:
            val newDrawerContent = ":ID:       $id\n$drawerContent"
            content.replaceFirst(
                PROPERTIES_DRAWER_REGEX,
                ":PROPERTIES:\n$newDrawerContent:END:",
            )
        }
    }

    private fun prependNewDrawer(content: String, id: String): String {
        val drawer = buildString {
            appendLine(":PROPERTIES:")
            appendLine(":ID:       $id")
            appendLine(":END:")
        }

        // If content starts with #+title: or similar, prepend drawer before it
        // Otherwise just prepend at the very start
        return drawer + content
    }

    /**
     * Returns the display name for a note based on its content.
     *
     * @param content The note content
     * @param defaultName Fallback if no title found (default: "New note")
     * @return Truncated title for UI display, or default name
     */
    fun getDisplayName(content: String, defaultName: String = "New note"): String {
        val title = extractTitle(content)
        return if (title == "untitled") {
            defaultName
        } else {
            truncateForUi(title)
        }
    }

    /**
     * Builds an org-mode link to another note, preferring a stable `id:` link
     * (survives renames) and falling back to a `file:` link when no org id
     * exists yet. The title is used as the visible label unless it contains
     * `[` or `]`, which would break the link's bracket syntax.
     */
    fun buildNoteLink(title: String, orgId: String?, path: String): String {
        val label = title.takeUnless { it.isBlank() || it.contains('[') || it.contains(']') }
        val target = orgId?.takeUnless { it.isBlank() }?.let { "id:$it" } ?: "file:$path"
        return if (label != null) "[[$target][$label]]" else "[[$target]]"
    }
}
