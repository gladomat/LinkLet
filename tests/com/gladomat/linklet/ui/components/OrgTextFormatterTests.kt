package com.gladomat.linklet.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Range
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.parser.org.buildOrgDisplayText
import com.gladomat.linklet.ui.components.ORG_EXTERNAL_LINK_ANNOTATION_TAG
import com.gladomat.linklet.ui.components.ORG_LINK_ANNOTATION_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgTextFormatterTests {

    private val palette = OrgTextPalette(
        headingLevel1 = Color(0xFFFFBF00),
        headingLevel2 = Color(0xFFE83F6F),
        headingLevel3 = Color(0xFF2274A5),
        bulletColor = Color(0xFF32936F),
        linkColor = Color.Magenta,
        codeBackground = Color(0x332274A5),
        codeTextColor = Color(0xFF2274A5),
        verbatimBackground = Color(0x3332936F),
        verbatimTextColor = Color(0xFF32936F),
        searchHighlightBackground = Color(0xFFFFF59D),
        activeSearchHighlightBackground = Color(0xFFFFCC80),
    )

    @Test
    fun `formatting removes heading markers and renders bullets`() {
        val content = """
            * Heading One
            ** Subheading
            - bullet entry
            1. numbered
        """.trimIndent()

        val annotated = buildOrgContentAnnotatedString(content, emptyList(), palette)

        assertEquals(
            "Heading One\nSubheading\n  • bullet entry\n  1. numbered",
            annotated.text,
        )
    }

    @Test
    fun `inline emphasis and links are annotated`() {
        val content = "Mix *bold* /italic/ _underline_ =verb= ~code~ +gone+ [[id:target][alias]]"
        val links = listOf(
            NoteLink(
                fromId = NoteId("note"),
                target = LinkTarget.Id("target"),
                label = "alias",
                resolvedPath = "notes/target.org",
            ),
        )

        val annotated = buildOrgContentAnnotatedString(content, links, palette)

        assertEquals(
            "Mix bold italic underline verb code gone alias",
            annotated.text.trim(),
        )
        val linkAnnotation = annotated.findAnnotationRange("alias")
        assertEquals("notes/target.org", linkAnnotation?.item)
    }

    @Test
    fun `unresolved links stay as original markup`() {
        val annotated = buildOrgContentAnnotatedString(
            content = "Broken [[id:missing][Missing]].",
            links = emptyList(),
            palette = palette,
        )

        assertEquals("Broken [[id:missing][Missing]].", annotated.text)
        assertTrue(annotated.getStringAnnotations(ORG_LINK_ANNOTATION_TAG, 0, annotated.length).isEmpty())
    }

    @Test
    fun `url text is annotated for external navigation`() {
        val annotated = buildOrgContentAnnotatedString(
            content = "Visit https://example.com or www.kotlinlang.org for docs.",
            links = emptyList(),
            palette = palette,
        )

        val first = annotated.findExternalAnnotation("https://example.com")
        assertEquals("https://example.com", first?.item)
        val second = annotated.findExternalAnnotation("www.kotlinlang.org")
        assertEquals("https://www.kotlinlang.org", second?.item)
    }

    @Test
    fun `applyHighlights adds spans for matches and active match`() {
        val base = buildOrgContentAnnotatedString(
            content = "Hello hello HELLO",
            links = emptyList(),
            palette = palette,
        )

        val highlighted = applyHighlights(
            base = base,
            ranges = listOf(0..4, 6..10, 12..16),
            activeRange = 6..10,
            matchBackground = palette.searchHighlightBackground,
            activeBackground = palette.activeSearchHighlightBackground,
        )

        val matchSpans = highlighted.spanStyles.filter { it.item.background == palette.searchHighlightBackground }
        assertEquals(3, matchSpans.size)
        val activeSpans = highlighted.spanStyles.filter { it.item.background == palette.activeSearchHighlightBackground }
        assertEquals(1, activeSpans.size)
    }

    @Test
    fun `display text formatter matches annotated output text`() {
        val content = """
            * Heading One
            - bullet entry with [[id:target][alias]]
            Mix *bold* /italic/ _underline_ =verb= ~code~ +gone+
        """.trimIndent()
        val links = listOf(
            NoteLink(
                fromId = NoteId("note"),
                target = LinkTarget.Id("target"),
                label = "alias",
                resolvedPath = "notes/target.org",
            ),
        )

        val displayText = buildOrgDisplayText(content, links)
        val annotatedText = buildOrgContentAnnotatedString(content, links, palette).text

        assertEquals(annotatedText, displayText)
    }

    private fun AnnotatedString.findAnnotationRange(text: String): Range<String>? {
        val start = this.text.indexOf(text)
        val end = start + text.length
        return if (start >= 0) {
            getStringAnnotations(ORG_LINK_ANNOTATION_TAG, start, end).firstOrNull()
        } else null
    }

    private fun AnnotatedString.findExternalAnnotation(text: String): Range<String>? {
        val start = this.text.indexOf(text)
        val end = start + text.length
        return if (start >= 0) {
            getStringAnnotations(ORG_EXTERNAL_LINK_ANNOTATION_TAG, start, end).firstOrNull()
        } else null
    }
}
