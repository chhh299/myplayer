package app.gyrolet.mpvrx.domain.network

import androidx.compose.runtime.Immutable
import app.gyrolet.mpvrx.utils.storage.FileTypeUtils

/**
 * Represents a file or directory on a network share
 */
@Immutable
data class NetworkFile(
  val name: String,
  val path: String,
  val size: Long,
  val isDirectory: Boolean,
  val lastModified: Long = 0,
  val mimeType: String? = null,
)

fun NetworkFile.isAudioFile(): Boolean {
  if (isDirectory || isM3uFile()) return false
  val mime = mimeType?.lowercase()
  if (mime?.startsWith("audio/") == true) return true
  val cleanName = name.substringBefore('?').substringBefore('#')
  val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
  return extension in FileTypeUtils.AUDIO_EXTENSIONS
}

fun NetworkFile.isVideoFile(): Boolean {
  if (isDirectory || isM3uFile()) return false
  val mime = mimeType?.lowercase()
  if (mime?.startsWith("video/") == true) return true
  val cleanName = name.substringBefore('?').substringBefore('#')
  val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
  return extension in FileTypeUtils.VIDEO_EXTENSIONS
}

fun NetworkFile.isPlayableMediaFile(): Boolean {
  return isVideoFile() || isAudioFile() || isM3uFile()
}

fun NetworkFile.isM3uFile(): Boolean {
  if (isDirectory) return false
  val lowerName = name.lowercase()
  val lowerPath = path.substringBefore('?').lowercase()
  return lowerName.endsWith(".m3u") ||
    lowerName.endsWith(".m3u8") ||
    lowerPath.endsWith(".m3u") ||
    lowerPath.endsWith(".m3u8") ||
    mimeType?.lowercase() in setOf(
      "application/x-mpegurl",
      "application/vnd.apple.mpegurl",
      "audio/x-mpegurl",
      "audio/mpegurl",
    )
}
