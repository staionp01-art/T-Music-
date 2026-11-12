package com.tmusic.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9C6BFF), background = Color(0xFF0E0E12), surface = Color(0xFF17171D))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
    if (granted) Library() else Box(Modifier.fillMaxSize(), Alignment.Center) {
        Button(onClick = { launcher.launch(perm) }) { Text("السماح بالوصول إلى الأغاني") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Library() {
    val ctx = LocalContext.current
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var query by remember { mutableStateOf("") }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { songs = withContext(Dispatchers.IO) { loadSongs(ctx) } }

    DisposableEffect(Unit) {
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({
            val c = f.get(); controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onMediaMetadataChanged(m: MediaMetadata) {
                    title = m.title?.toString() ?: ""; artist = m.artist?.toString() ?: ""
                }
            })
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(f) }
    }

    val shown = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }

    fun play(index: Int) {
        val c = controller ?: return
        c.setMediaItems(shown.map {
            MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(
                MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setArtworkUri(it.art).build()
            ).build()
        }, index, 0)
        c.prepare(); c.play()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (title.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton({ controller?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null) }
                    FilledIconButton({ controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    }
                    IconButton({ controller?.seekToNext() }) { Icon(Icons.Default.SkipNext, null) }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            Text("T Music", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("ابحث عن أغنية أو فنان") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                    Row(Modifier.fillMaxWidth().clickable { play(i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = s.art, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                            error = androidx.compose.ui.graphics.painter.ColorPainter(Color(0xFF2A2A35))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
