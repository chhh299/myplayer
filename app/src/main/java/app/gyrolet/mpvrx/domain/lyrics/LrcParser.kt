package app.gyrolet.mpvrx.domain.lyrics

object LrcParser {
  private val TIMESTAMP_REGEX = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
  private val OFFSET_REGEX = Regex("\\[offset:\\s*([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)

  fun parse(content: String): LyricsDocument {
    if (content.isBlank()) return LyricsDocument()

    var offsetMs = 0L
    val rawLines = mutableListOf<LyricLine>()
    var hasInlineBilingual = false

    val lines = content.lines()
    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) continue

      val offsetMatch = OFFSET_REGEX.find(trimmed)
      if (offsetMatch != null) {
        offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
        continue
      }

      val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
      if (matches.isEmpty()) continue

      // Strip all timestamps from the line to get the lyric text
      var lyricText = trimmed
      for (match in matches) {
        lyricText = lyricText.replace(match.value, "")
      }
      lyricText = lyricText.trim()

      var text = lyricText
      var translation: String? = null

      if (lyricText.contains(" / ")) {
        val parts = lyricText.split(" / ", limit = 2)
        text = parts[0].trim()
        translation = parts[1].trim()
        hasInlineBilingual = true
      } else if (lyricText.contains(" // ")) {
        val parts = lyricText.split(" // ", limit = 2)
        text = parts[0].trim()
        translation = parts[1].trim()
        hasInlineBilingual = true
      }

      for (match in matches) {
        val min = match.groupValues[1].toLongOrNull() ?: 0L
        val sec = match.groupValues[2].toLongOrNull() ?: 0L
        val fractionStr = match.groupValues.getOrNull(3).orEmpty()
        val fractionMs = when (fractionStr.length) {
          1 -> (fractionStr.toLongOrNull() ?: 0L) * 100
          2 -> (fractionStr.toLongOrNull() ?: 0L) * 10
          3 -> fractionStr.toLongOrNull() ?: 0L
          else -> 0L
        }
        val timeMs = min * 60_000L + sec * 1_000L + fractionMs
        rawLines.add(LyricLine(timeMs = timeMs, text = text, translation = translation))
      }
    }

    if (rawLines.isEmpty()) return LyricsDocument(offsetMs = offsetMs)

    // Sort by timestamp
    val sortedLines = rawLines.sortedBy { it.timeMs }

    // Check for paired duplicate timestamps:
    // e.g. line A at [00:01.50] and line B at [00:01.50] -> combine into bilingual line
    val mergedLines = mutableListOf<LyricLine>()
    var pairedBilingualFound = false

    var i = 0
    while (i < sortedLines.size) {
      val current = sortedLines[i]
      if (i + 1 < sortedLines.size && sortedLines[i + 1].timeMs == current.timeMs && current.translation == null && sortedLines[i + 1].translation == null) {
        val next = sortedLines[i + 1]
        mergedLines.add(
          LyricLine(
            timeMs = current.timeMs,
            text = current.text,
            translation = next.text,
          )
        )
        pairedBilingualFound = true
        i += 2
      } else {
        mergedLines.add(current)
        i += 1
      }
    }

    val isBilingual = hasInlineBilingual || pairedBilingualFound
    return LyricsDocument(
      lines = mergedLines,
      isBilingual = isBilingual,
      offsetMs = offsetMs,
    )
  }
}
