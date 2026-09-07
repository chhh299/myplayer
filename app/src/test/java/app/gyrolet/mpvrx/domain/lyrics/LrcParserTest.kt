package app.gyrolet.mpvrx.domain.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class LrcParserTest {

  @Test
  fun testStandardLrcParsing() {
    val lrcContent = """
      [ti:Test Song]
      [ar:Artist Name]
      [al:Album Name]
      [00:01.50]First line of lyrics
      [00:04.20]Second line of lyrics
      [00:10.00]Third line of lyrics
    """.trimIndent()

    val document = LrcParser.parse(lrcContent)
    assertEquals(3, document.lines.size)

    assertEquals(1500L, document.lines[0].timeMs)
    assertEquals("First line of lyrics", document.lines[0].text)

    assertEquals(4200L, document.lines[1].timeMs)
    assertEquals("Second line of lyrics", document.lines[1].text)

    assertEquals(10000L, document.lines[2].timeMs)
    assertEquals("Third line of lyrics", document.lines[2].text)
  }

  @Test
  fun testMultiTimestampPerLine() {
    val lrcContent = """
      [00:01.00][00:05.00]Chorus line repeated
    """.trimIndent()

    val document = LrcParser.parse(lrcContent)
    assertEquals(2, document.lines.size)
    assertEquals(1000L, document.lines[0].timeMs)
    assertEquals("Chorus line repeated", document.lines[0].text)
    assertEquals(5000L, document.lines[1].timeMs)
    assertEquals("Chorus line repeated", document.lines[1].text)
  }

  @Test
  fun testInlineBilingualLrc() {
    val lrcContent = """
      [00:01.50]Hello world / 你好世界
      [00:04.00]Good morning / 早上好
    """.trimIndent()

    val document = LrcParser.parse(lrcContent)
    assertEquals(2, document.lines.size)
    assertEquals("Hello world", document.lines[0].text)
    assertEquals("你好世界", document.lines[0].translation)
    assertTrue(document.isBilingual)
  }

  @Test
  fun testPairedDuplicateTimestampBilingualLrc() {
    val lrcContent = """
      [00:01.50]Hello world
      [00:01.50]你好世界
      [00:04.00]Good morning
      [00:04.00]早上好
    """.trimIndent()

    val document = LrcParser.parse(lrcContent)
    assertEquals(2, document.lines.size)
    assertEquals(1500L, document.lines[0].timeMs)
    assertEquals("Hello world", document.lines[0].text)
    assertEquals("你好世界", document.lines[0].translation)
    assertEquals(4000L, document.lines[1].timeMs)
    assertEquals("Good morning", document.lines[1].text)
    assertEquals("早上好", document.lines[1].translation)
    assertTrue(document.isBilingual)
  }

  @Test
  fun testActiveLineFinding() {
    val lines = listOf(
      LyricLine(1000L, "Line 1"),
      LyricLine(3000L, "Line 2"),
      LyricLine(6000L, "Line 3"),
    )
    val doc = LyricsDocument(lines)

    assertEquals(-1, doc.findActiveLineIndex(500L))
    assertEquals(0, doc.findActiveLineIndex(1000L))
    assertEquals(0, doc.findActiveLineIndex(2500L))
    assertEquals(1, doc.findActiveLineIndex(3000L))
    assertEquals(1, doc.findActiveLineIndex(5999L))
    assertEquals(2, doc.findActiveLineIndex(6000L))
    assertEquals(2, doc.findActiveLineIndex(10000L))
  }
}
