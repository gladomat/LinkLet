 Plan: Hide org drawers behind in-place expandable pills

  Context

  Section-level drawers (:PROPERTIES:, :LOGBOOK:, custom :NAME:…:END:) leak into the rendered note body

  because parseContentToBlocks() has no drawer state — the lines become OrgBlock.Paragraphs. File-level

  properties already have a collapsible card; section drawers need to be hidden behind a tappable pill, in

  document order.

  Step 1 — Parser: new OrgBlock.Drawer type

  data/parser/org/OrgBlock.kt

  /**

   * A drawer `:NAME:` ... `:END:`). Hidden behind a pill in the UI.

   * @param name Drawer name, uppercased (e.g. "PROPERTIES", "LOGBOOK").

   * @param lines Raw content lines between the delimiters.

   * @param properties Parsed key/value pairs for `:KEY: value` lines (used for PROPERTIES rendering).

   */

  data class Drawer(

      val name: String,

      val lines: List&lt;String&gt;,

      val properties: Map&lt;String, String&gt;,

  ) : OrgBlock

  data/parser/org/OrgDocumentParser.kt

  - Add DrawerStartRegex = Regex("""^:([A-Za-z0-9_-]+):\s*$""") — a standalone :NAME: with no value (distinct

  from PropertyLineRegex's :KEY: value). Exclude :END: itself.

  - Add ParseState.InDrawer(name: String, lines: MutableList&lt;String&gt;) to the state machine:

    - Entered only from Idle (so drawers can't start inside #+BEGIN_… blocks or tables) in handleIdleLine(),

  after the begin-block and table checks; flush the paragraph buffer first.

    - Line matching :END: (case-insensitive, trimmed) closes it → emit OrgBlock.Drawer(name.uppercase(),

  lines, parseDrawerProperties(lines)).

    - Unclosed drawer at EOF/section end (final-state handling at OrgDocumentParser.kt:224): fall back —

  replay ":$name:" + buffered lines through paragraph handling so no content is silently swallowed. Never

  lose text.

  - parseDrawerProperties(lines): apply PropertyLineRegex per line, uppercase keys — same semantics as

  extractLeadingDrawerProperties().

  - Keep [OrgSection.properties](http://OrgSection.properties) working (attachment ID/DIR resolution depends on it): replace

  extractLeadingDrawerProperties(contentLines) in toSection() with deriving from the parsed blocks —

  blocks.firstOrNull { it is Drawer &amp;&amp; [it.name](http://it.name) == "PROPERTIES" }?.properties ?: emptyMap(). One parse path,

  no duplicated drawer logic. (Leading-position semantics relax to "first PROPERTIES drawer in section" —

  acceptable.)

  - Preface gets the same treatment for free (prefaceBlocks uses the same function). File-level top drawer is

  already consumed by extractMetadata() before body parsing — unchanged.

  Step 2 — UI: DrawerPill composable

  New file ui/screens/note/DrawerBlock.kt (or into OrgBlockRenderers.kt):

  - DrawerBlockView(block: OrgBlock.Drawer, expanded: Boolean, onToggle: () -&gt; Unit, onCopyId: ((String) -&gt;

  Unit)?)

  - Collapsed: small rounded pill — lowercase monospace drawer name + chevron (KeyboardArrowRight → rotates

  to KeyboardArrowDown when expanded, matching PropertiesSection's icons). surfaceVariant tonal background to

  distinguish from TagPill (TagRow.kt:44).

  - Expanded (AnimatedVisibility, like PropertiesSection at NoteViewScreen.kt:979):

    - name == "PROPERTIES" → key/value rows reusing the file-level PropertiesSection card layout (extract its

  row rendering into a shared private composable if convenient).

    - Other drawers → raw lines in a monospace card (style like ExampleBlock).

  - Copy-link action: in the PROPERTIES expanded view, the ID row is tappable → copies [[id:&lt;value&gt;][&lt;section

  title&gt;]] via ClipboardManager, with a snackbar/Toast "Link copied". Section title must be threaded in (see

  Step 3).

  Step 3 — Wire into NoteViewScreen.kt

  - State: val drawerExpansion = remember(note.content) { mutableStateMapOf&lt;String, Boolean&gt;() } — keyed by

  blockId, collapsed by default, ephemeral (matches propertiesExpanded behavior).

  - buildNoteBodyItems() (NoteViewScreen.kt:748): no structural change needed — Drawer flows through as a

  normal BlockItem. Add the section title to BlockItem (nullable sectionTitle: String?) so the copy-link

  action can build [[id:…][title]]; preface drawers pass null (copy [[id:…]] without description).

  - Render dispatch (NoteViewScreen.kt:692, BlockItem branch): intercept item.block is OrgBlock.Drawer →

  render DrawerBlockView with drawerExpansion[item.blockId] state; everything else goes to OrgBlockView as

  today.

  Step 4 — Exhaustive when sites over OrgBlock

  Three compile-error sites once Drawer is added (this is the safety net — list verified by grep):

  1. ui/components/OrgBlockRenderers.kt:102 (OrgBlockView) — add a Drawer branch; either render nothing

  (handled upstream in Step 3) or render the pill directly here if the dispatch ends up cleaner there.

  2. viewmodel/note/NoteViewViewModel.kt:443 (toSearchBlocks) — decision: drawers are excluded from in-note

  search (emptyList()). Matching inside a collapsed pill would scroll to something invisible; consistent with

  file-level properties not being searchable.

  3. NoteViewScreen.kt:821 (block-id index map) — add Drawer to whatever branch non-table blocks take.

  Step 5 — Tests

  New app/src/test/java/com/gladomat/linklet/data/parser/org/OrgDocumentParserTest.kt (JUnit 4, no parser

  tests exist yet):

  - Leading :PROPERTIES: drawer under a heading → Drawer block emitted, no Paragraph containing :PROPERTIES:,

  and [OrgSection.properties](http://OrgSection.properties)["ID"] still populated (attachment regression guard).

  - Mid-body custom drawer (:NOTES:…:END:) → in-place Drawer block between paragraphs, document order

  preserved.

  - :LOGBOOK: with clock lines → Drawer with raw lines, properties empty-ish.

  - Unclosed drawer → falls back to paragraph text, nothing lost.

  - :KEY: value line alone (no drawer) → stays a paragraph (regex doesn't false-positive).

  - Drawer-like lines inside #+BEGIN_SRC → stay inside the source block.

  - Drawer in preface → prefaceBlocks contains Drawer.

  - Blank lines between heading and drawer → still extracted (current extractLeadingDrawerProperties skips

  blanks; firstOrNull-based derivation keeps this).

  Step 6 — Verify

  - ./gradlew :app:testDebugUnitTest (with Android Studio JBR 21 per build env notes).

  - Build + install, open a note with subheading property drawers: drawer text gone from body, pill present,

  tap expands key/value card, ID row copies [[id:…][Heading]], re-collapse works, file-level card unchanged.

  Out of scope (explicitly decided): unifying the top-of-note PropertiesSection with the pill mechanism;

  persisting expansion state; editing properties from the pill.