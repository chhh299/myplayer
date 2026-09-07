package app.gyrolet.mpvrx.ui.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.gyrolet.mpvrx.ui.icons.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.lyrics.LyricLine
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

@Composable
fun LyricsOverlay(
  viewModel: PlayerViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentLyrics by viewModel.currentLyrics.collectAsState()
  val timePosSec by MPVLib.propInt["time-pos"].collectAsState()

  val currentTimeMs = (timePosSec ?: 0) * 1000L
  val activeIndex = remember(currentLyrics, currentTimeMs) {
    currentLyrics?.findActiveLineIndex(currentTimeMs) ?: -1
  }

  val listState = rememberLazyListState()
  val isDragged by listState.interactionSource.collectIsDraggedAsState()
  var lastUserInteractionTime by remember { mutableLongStateOf(0L) }

  if (isDragged) {
    lastUserInteractionTime = System.currentTimeMillis()
  }

  // Smooth scroll to active lyric line, unless the user recently dragged/scrolled
  LaunchedEffect(activeIndex, isDragged) {
    if (activeIndex >= 0 && !isDragged) {
      val timeSinceInteraction = System.currentTimeMillis() - lastUserInteractionTime
      if (timeSinceInteraction > 3500L) {
        val scrollTarget = (activeIndex - 2).coerceAtLeast(0)
        listState.animateScrollToItem(scrollTarget)
      }
    }
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 16.dp),
    ) {
      // Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.RoundedFilled.Subtitles,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Text(
            text = stringResource(R.string.ui_lyrics_panel),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(
            imageVector = Icons.RoundedFilled.Close,
            contentDescription = stringResource(R.string.ui_close),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      val lines = currentLyrics?.lines.orEmpty()
      if (lines.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.RoundedFilled.Subtitles,
              contentDescription = null,
              modifier = Modifier.size(56.dp),
              tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
            Text(
              text = stringResource(R.string.ui_no_lyrics),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.outline,
            )
          }
        }
      } else {
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp, start = 24.dp, end = 24.dp),
          verticalArrangement = Arrangement.spacedBy(18.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          itemsIndexed(
            items = lines,
            key = { index, line -> "${index}_${line.timeMs}" },
          ) { index, line ->
            val isActive = index == activeIndex
            LyricLineItem(
              line = line,
              isActive = isActive,
              onClick = {
                viewModel.seekTo((line.timeMs / 1000L).toInt())
                lastUserInteractionTime = 0L
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LyricLineItem(
  line: LyricLine,
  isActive: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 6.dp, horizontal = 12.dp),
  ) {
    Text(
      text = line.text,
      style = if (isActive) {
        MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp,
          lineHeight = 28.sp,
        )
      } else {
        MaterialTheme.typography.bodyLarge.copy(
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
          lineHeight = 24.sp,
        )
      },
      color = if (isActive) primaryColor else onSurfaceVariantColor.copy(alpha = 0.55f),
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )

    if (!line.translation.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = line.translation,
        style = if (isActive) {
          MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
          )
        } else {
          MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
          )
        },
        color = if (isActive) primaryColor.copy(alpha = 0.85f) else onSurfaceVariantColor.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
