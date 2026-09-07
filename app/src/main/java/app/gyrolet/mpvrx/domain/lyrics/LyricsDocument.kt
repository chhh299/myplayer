package app.gyrolet.mpvrx.domain.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(
  val timeMs: Long,
  val text: String,
  val translation: String? = null,
)

@Immutable
data class LyricsDocument(
  val lines: List<LyricLine> = emptyList(),
  val isBilingual: Boolean = false,
  val offsetMs: Long = 0L,
) {
  fun findActiveLineIndex(currentTimeMs: Long): Int {
    if (lines.isEmpty()) return -1
    val targetTime = currentTimeMs - offsetMs
    if (targetTime < lines.first().timeMs) return -1
    for (i in lines.indices.reversed()) {
      if (targetTime >= lines[i].timeMs) {
        return i
      }
    }
    return -1
  }
}
