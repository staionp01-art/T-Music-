package com.tmusic.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0B10)
private val Card = Color(0xFF17171F)
private val Accent = Color(0xFF9C6BFF)

class Pb {
    var c by mutableStateOf<MediaController?>(null)
    var playing by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<Uri?>(null)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(0)

    fun play(list: List<Song>, i: Int, shuf: Boolean = false) {
        val p = c ?: return
        if (list.isEmpty()) return
        p.setMediaItems(list.map { s ->
            MediaItem.Builder().setUri(s.uri).setMediaId(s.id.toString()).setMediaMetadata(
                MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setArtworkUri(s.art).build()
            ).build()
        }, if (shuf) list.indices.random() else i, 0)
        p.shuffleModeEnabled = shuf
        p.prepare(); p.play()
    }
}

fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Root() }
            }
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var ok by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val l = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    LaunchedEffect(Unit) { if (!ok) l.launch(perm) }
    if (ok) App() else Box(Modifier.fillMaxSize(), Alignment.Center) {
        Button(onClick = { l.launch(perm) }) { Text("السماح بالوصول إلى الأغاني") }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val pb = remember { Pb() }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { songs = withContext(Dispatchers.IO) { loadSongs(ctx) } }
    DisposableEffect(Unit) {
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({
            val c = f.get(); pb.c = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { pb.playing = isPlaying }
                override fun onMediaMetadataChanged(m: MediaMetadata) {
                    pb.title = m.title?.toString() ?: ""; pb.artist = m.artist?.toString() ?: ""; pb.art = m.artworkUri
                }
                override fun onShuffleModeEnabledChanged(s: Boolean) { pb.shuffle = s }
                override fun onRepeatModeChanged(r: Int) { pb.repeat = r }
            })
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(f) }
    }
    LaunchedEffect(pb.c) {
        while (true) { pb.c?.let { pb.pos = it.currentPosition; pb.dur = it.duration.coerceAtLeast(0L) }; delay(400) }
    }
    BackHandler(full) { full = false }
    val tabs = listOf("الأغاني" to Icons.Default.MusicNote, "الألبومات" to Icons.Default.Album, "الفنانون" to Icons.Default.Person)
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Bg, bottomBar = {
            Column {
                if (pb.title.isNotEmpty()) Mini(pb) { full = true }
                NavigationBar(containerColor = Card) {
                    tabs.forEachIndexed { i, (name, ic) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, null) }, label = { Text(name) })
                    }
                }
            }
        }) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) { 0 -> SongsTab(songs, pb); 1 -> AlbumsTab(songs, pb); else -> ArtistsTab(songs, pb) }
            }
        }
        if (full) NowPlaying(pb) { full = false }
    }
}

@Composable
fun Art(u: Any?, m: Modifier) = AsyncImage(
    model = u, contentDescription = null, contentScale = ContentScale.Crop,
    modifier = m.background(Card), error = ColorPainter(Card)
)

@Composable
fun Title(t: String) = Text(t, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

@Composable
fun SongsTab(songs: List<Song>, pb: Pb) {
    var q by remember { mutableStateOf("") }
    val list = remember(songs, q) { if (q.isBlank()) songs else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) } }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
        item {
            Title("T Music")
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, shape = RoundedCornerShape(28.dp),
                placeholder = { Text("ابحث عن أغنية أو فنان") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ pb.play(list, 0) }, Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text(" تشغيل") }
                FilledTonalButton({ pb.play(list, 0, true) }, Modifier.weight(1f)) { Icon(Icons.Default.Shuffle, null); Text(" عشوائي") }
            }
            Text("${list.size} أغنية", color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            Row(Modifier.fillMaxWidth().clickable { pb.play(list, i) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s.art, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (pb.title == s.title) Accent else Color.White, fontWeight = FontWeight.Medium)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Color.Gray)
                }
                Text(fmt(s.duration), fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AlbumsTab(songs: List<Song>, pb: Pb) {
    val albums = remember(songs) { songs.groupBy { it.albumId }.values.toList() }
    LazyVerticalGrid(
        GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(2) }) { Title("الألبومات") }
        items(albums) { a ->
            Column(Modifier.clickable { pb.play(a, 0) }) {
                Art(a[0].art, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                Text(a[0].album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                Text(a[0].artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ArtistsTab(songs: List<Song>, pb: Pb) {
    val artists = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first } }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
        item { Title("الفنانون") }
        items(artists) { (name, list) ->
            Row(Modifier.fillMaxWidth().clickable { pb.play(list, 0) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(list[0].art, Modifier.size(56.dp).clip(CircleShape))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${list.size} أغنية", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun Mini(pb: Pb, open: () -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2045)).clickable(onClick = open)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(pb.art, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(pb.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(pb.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Color.Gray)
            }
            IconButton({ pb.c?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                Icon(if (pb.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(30.dp))
            }
            IconButton({ pb.c?.seekToNext() }) { Icon(Icons.Default.SkipNext, null) }
        }
        LinearProgressIndicator(
            progress = { if (pb.dur > 0) pb.pos.toFloat() / pb.dur else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent
        )
    }
}

@Composable
fun NowPlaying(pb: Pb, close: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A2670), Bg, Bg)))
            .systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(34.dp)) }
            Spacer(Modifier.weight(1f))
            Text("يتم التشغيل الآن", color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Art(pb.art, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
        Spacer(Modifier.weight(1f))
        Text(pb.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(pb.artist, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Slider(
            value = if (pb.dur > 0) pb.pos.toFloat() / pb.dur else 0f,
            onValueChange = { v -> val t = (v * pb.dur).toLong(); pb.pos = t; pb.c?.seekTo(t) },
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pb.pos), fontSize = 12.sp, color = Color.Gray); Text(fmt(pb.dur), fontSize = 12.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ pb.c?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }) {
                Icon(Icons.Default.Shuffle, null, tint = if (pb.shuffle) Accent else Color.Gray)
            }
            IconButton({ pb.c?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(40.dp)) }
            FilledIconButton({ pb.c?.let { if (it.isPlaying) it.pause() else it.play() } }, Modifier.size(72.dp)) {
                Icon(if (pb.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(40.dp))
            }
            IconButton({ pb.c?.seekToNext() }) { Icon(Icons.Default.SkipNext, null, Modifier.size(40.dp)) }
            IconButton({ pb.c?.let { it.repeatMode = (it.repeatMode + 2) % 3 } }) {
                Icon(if (pb.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (pb.repeat != 0) Accent else Color.Gray)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
