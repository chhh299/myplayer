package app.gyrolet.mpvrx.ui.player.audio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.components.RemoteImage
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AudioPlayerOverlay(
  viewModel: PlayerViewModel,
  mediaTitle: String,
  onOpenLyrics: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val networkCoverUri by viewModel.networkCoverUri.collectAsState()
  val hasAlbumArt by viewModel.hasAlbumArt.collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val currentLyrics by viewModel.currentLyrics.collectAsState()
  val timePosSec by MPVLib.propInt["time-pos"].collectAsState()

  val artist = remember(mediaTitle) {
    runCatching {
      MPVLib.getPropertyString("metadata/by-key/artist")
        ?: MPVLib.getPropertyString("metadata/by-key/Artist")
    }.getOrNull()
  }

  val album = remember(mediaTitle) {
    runCatching {
      MPVLib.getPropertyString("metadata/by-key/album")
        ?: MPVLib.getPropertyString("metadata/by-key/Album")
    }.getOrNull()
  }

  val currentTimeMs = (timePosSec ?: 0) * 1000L
  val activeLyricLine = remember(currentLyrics, currentTimeMs) {
    currentLyrics?.let { doc ->
      val idx = doc.findActiveLineIndex(currentTimeMs)
      if (idx in doc.lines.indices) doc.lines[idx] else null
    }
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp),
    ) {
      // Album Artwork Card with Ambient Glow
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(bottom = 28.dp),
      ) {
        // Ambient glow background
        Box(
          modifier = Modifier
            .size(260.dp)
            .background(
              brush = Brush.radialGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                  MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                  Color.Transparent,
                )
              ),
              shape = CircleShape,
            )
        )

        // Cover Card
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (hasAlbumArt && networkCoverUri == null) Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
          modifier = Modifier
            .size(240.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            when {
              networkCoverUri != null -> {
                AudioCoverImage(
                  uri = networkCoverUri!!,
                  modifier = Modifier.fillMaxSize(),
                )
              }
              hasAlbumArt -> {
                // mpv's native surface underneath is already decoding and displaying the album art track
                // Keep the box transparent so it shows through the card
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                )
              }
              else -> {
                // Turntable Vinyl record fallback
                TurntableVinylPlaceholder(isPlaying = paused == false)
              }
            }
          }
        }
      }

      // Track Title
      Text(
        text = mediaTitle,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .basicMarquee(),
      )

      // Artist & Album
      val subtitleText = when {
        !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist — $album"
        !artist.isNullOrBlank() -> artist
        !album.isNullOrBlank() -> album
        else -> null
      }
      if (subtitleText != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = subtitleText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 24.dp),
        )
      }

      // Quick lyrics preview banner
      if (activeLyricLine != null) {
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
          modifier = Modifier
            .clickable { onOpenLyrics() }
            .padding(horizontal = 8.dp),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          ) {
            Text(
              text = activeLyricLine.text,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
              color = MaterialTheme.colorScheme.primary,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (!activeLyricLine.translation.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = activeLyricLine.translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      } else if (currentLyrics != null && currentLyrics!!.lines.isNotEmpty()) {
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
          modifier = Modifier.clickable { onOpenLyrics() },
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.RoundedFilled.Subtitles,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
              text = stringResource(R.string.ui_lyrics),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AudioCoverImage(uri: String, modifier: Modifier = Modifier) {
  if (uri.startsWith("http://") || uri.startsWith("https://")) {
    RemoteImage(
      url = uri,
      contentDescription = null,
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  } else {
    var localBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
      localBitmap = withContext(Dispatchers.IO) {
        val file = File(uri)
        if (file.exists()) {
          BitmapFactory.decodeFile(file.absolutePath)
        } else null
      }
    }
    if (localBitmap != null) {
      Image(
        bitmap = localBitmap!!.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
      )
    } else {
      TurntableVinylPlaceholder(isPlaying = false)
    }
  }
}

@Composable
private fun TurntableVinylPlaceholder(isPlaying: Boolean) {
  val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "vinyl_angle",
  )

  val currentRotation = if (isPlaying) rotation else 0f

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF181818))
      .rotate(currentRotation),
    contentAlignment = Alignment.Center,
  ) {
    // Vinyl Grooves
    Box(
      modifier = Modifier
        .size(200.dp)
        .clip(CircleShape)
        .background(Color(0xFF222222)),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(150.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier
            .size(90.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.RoundedFilled.Audiotrack,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
  }
}
