package app.gyrolet.mpvrx.utils.storage

import app.gyrolet.mpvrx.domain.network.NetworkFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FileTypeAudioTest {

  @Test
  fun testAudioExtensionsRecognized() {
    val audioExts = listOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "mka", "ape")
    for (ext in audioExts) {
      assertTrue(
        ext in FileTypeUtils.AUDIO_EXTENSIONS,
        "Extension .$ext should be in AUDIO_EXTENSIONS",
      )
      val mime = FileTypeUtils.getMimeTypeFromExtension(ext)
      assertTrue(
        mime.startsWith("audio/"),
        "Mime for .$ext should start with audio/, got $mime",
      )
    }
  }

  @Test
  fun testNetworkFileIsAudioFile() {
    val mp3File = NetworkFile(
      name = "song.mp3",
      path = "/music/song.mp3",
      isDirectory = false,
      size = 1024 * 1024,
      lastModified = 0,
      mimeType = "audio/mpeg",
    )
    val flacFileWithoutMime = NetworkFile(
      name = "song.flac",
      path = "/music/song.flac",
      isDirectory = false,
      size = 1024 * 1024 * 20,
      lastModified = 0,
      mimeType = null,
    )
    val videoFile = NetworkFile(
      name = "movie.mp4",
      path = "/movies/movie.mp4",
      isDirectory = false,
      size = 1024 * 1024 * 500,
      lastModified = 0,
      mimeType = "video/mp4",
    )
    val dir = NetworkFile(
      name = "folder",
      path = "/music/folder",
      isDirectory = true,
      size = 0,
      lastModified = 0,
      mimeType = null,
    )

    assertTrue(mp3File.isAudioFile())
    assertTrue(flacFileWithoutMime.isAudioFile())
    assertFalse(videoFile.isAudioFile())
    assertFalse(dir.isAudioFile())

    assertTrue(mp3File.isPlayableMediaFile())
    assertTrue(flacFileWithoutMime.isPlayableMediaFile())
    assertTrue(videoFile.isPlayableMediaFile())
    assertFalse(dir.isPlayableMediaFile())
  }
}
