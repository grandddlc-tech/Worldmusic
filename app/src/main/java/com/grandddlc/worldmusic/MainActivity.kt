package com.grandddlc.worldmusic

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

private val Bg = Color(0xFF0B0D14)
private val Panel = Color(0xFF171B28)
private val Purple = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9298AA)

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val country: String = "نامشخص")

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WorldMusicApp(
            scan = { loadTracks() },
            play = { track -> try { player?.release(); player = MediaPlayer().apply { setDataSource(this@MainActivity, track.uri); prepare(); start() } } catch (_: Exception) { } },
            stop = { player?.pause() }
        ) }
    }
    private fun loadTracks(): List<Track> {
        val result = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "بدون عنوان"
                val artist = c.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "هنرمند ناشناس"
                result += Track(id, title, artist, Uri.withAppendedPath(collection, id.toString()))
            }
        }
        return result
    }
    override fun onDestroy() { player?.release(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMusicApp(scan: () -> List<Track>, play: (Track) -> Unit, stop: () -> Unit) {
    var tracks by remember { mutableStateOf(listOf<Track>()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var tab by remember { mutableStateOf("همه آهنگ‌ها") }
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var countryDialog by remember { mutableStateOf<Track?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { scanning = true; tracks = scan(); scanning = false }
    }
    fun startScan() {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            scanning = true
            tracks = scan()
            scanning = false
            tracks.forEach { t -> CountryLookup.lookup(t.artist) { country ->
                tracks = tracks.map { if (it.id == t.id && it.country == "نامشخص") it.copy(country = country) else it }
            } }
        } else launcher.launch(permission)
    }
    val countries = tracks.map { it.country }.filter { it != "نامشخص" }.distinct().sorted()
    val filtered = tracks.filter {
        (tab == "همه آهنگ‌ها" || if (tab == "کشورها" ) it.country != "نامشخص" else it.country == tab) &&
        (it.title.contains(query, true) || it.artist.contains(query, true))
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Purple, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(containerColor = Bg, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(selected = tab == "همه آهنگ‌ها", onClick = { tab = "همه آهنگ‌ها" }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("کتابخانه") })
                NavigationBarItem(selected = tab == "کشورها", onClick = { tab = "کشورها" }, icon = { Icon(Icons.Default.Public, null) }, label = { Text("کشورها") })
                NavigationBarItem(selected = tab == "علاقه‌مندی‌ها", onClick = { tab = "علاقه‌مندی‌ها" }, icon = { Icon(Icons.Default.FavoriteBorder, null) }, label = { Text("پسندیده‌ها") })
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().background(Bg).padding(pad).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WORLD MUSIC", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("موسیقیِ جهان تو", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    IconButton(onClick = { startScan() }) { Icon(Icons.Default.Refresh, "اسکن دوباره", tint = Purple) }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF33245E), Color(0xFF171B2B))), RoundedCornerShape(24.dp)).padding(20.dp)) {
                    Column {
                        Text("دنیای آهنگ‌هات، یک‌جا", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("کشف موسیقی‌ها بر اساس کشور هنرمند", color = Color(0xFFD2C9F6), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Stat(tracks.size.toString(), "آهنگ")
                            Spacer(Modifier.width(24.dp))
                            Stat(countries.size.toString(), "کشور شناسایی‌شده")
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { startScan() }, colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("اسکن گوشی")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("جست‌وجوی آهنگ یا خواننده…", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Panel, focusedBorderColor = Purple))
                Spacer(Modifier.height(16.dp))
                if (tab == "کشورها") {
                    Text("بر اساس کشور", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("همه کشورها") })
                        Text("${countries.size} کشور", color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(countries) { country ->
                            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable { tab = country }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, null, tint = Purple); Spacer(Modifier.width(12.dp)); Text(country, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text("${tracks.count { it.country == country }} آهنگ", color = Muted)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (tab == "علاقه‌مندی‌ها") "علاقه‌مندی‌ها" else "کتابخانه موسیقی", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f)); Text("${filtered.size} آهنگ", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (tracks.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LibraryMusic, null, tint = Purple, modifier = Modifier.size(54.dp))
                                Spacer(Modifier.height(12.dp)); Text(if (scanning) "در حال اسکن…" else "هنوز آهنگی اضافه نشده", fontWeight = FontWeight.Bold)
                                Text("برای پیدا کردن آهنگ‌های گوشی اسکن را بزن", color = Muted, fontSize = 12.sp)
                                Spacer(Modifier.height(14.dp)); Button(onClick = { startScan() }) { Text("اسکن آهنگ‌ها") }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                            items(filtered, key = { it.id }) { track ->
                                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable { selected = track; play(track) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Purple, Color(0xFF493A78))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Color.White) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                                        Text(track.country, color = Purple, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { countryDialog = track }) { Icon(Icons.Default.Flag, "ویرایش کشور", tint = Muted) }
                                    IconButton(onClick = { selected = track; play(track) }) { Icon(Icons.Default.PlayArrow, "پخش", tint = Purple) }
                                }
                            }
                        }
                    }
                }
                selected?.let { current ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).background(Color(0xFF25213A), RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = Purple); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(current.title, fontWeight = FontWeight.Bold, maxLines = 1); Text(current.artist, color = Muted, fontSize = 11.sp) }
                        IconButton(onClick = { stop(); selected = null }) { Icon(Icons.Default.Pause, "توقف", tint = Color.White) }
                    }
                }
            }
        }
        countryDialog?.let { track ->
            var value by remember(track.id) { mutableStateOf(track.country) }
            AlertDialog(onDismissRequest = { countryDialog = null }, title = { Text("کشور هنرمند") }, text = {
                Column {
                    Text("کشور مبدأ هنرمند را انتخاب یا وارد کن.", color = Muted)
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("نام کشور") })
                }
            }, confirmButton = { TextButton(onClick = { tracks = tracks.map { if (it.id == track.id) it.copy(country = value.ifBlank { "نامشخص" }) else it }; countryDialog = null }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = { countryDialog = null }) { Text("انصراف") } })
        }
    }
}

@Composable private fun Stat(value: String, label: String) {
    Column { Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(label, fontSize = 11.sp, color = Color(0xFFD2C9F6)) }
}

private object CountryLookup {
    private val executor = Executors.newSingleThreadExecutor()
    fun lookup(artist: String, done: (String) -> Unit) {
        if (artist == "هنرمند ناشناس") return
        executor.execute {
            var country = "نامشخص"
            try {
                val q = URLEncoder.encode("artist:\"$artist\"", "UTF-8")
                val conn = (URL("https://musicbrainz.org/ws/2/artist/?query=$q&fmt=json&limit=1").openConnection() as HttpURLConnection)
                conn.setRequestProperty("User-Agent", "WorldMusicAndroid/1.0 (local music library)")
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val artists = json.optJSONArray("artists")
                if (artists != null && artists.length() > 0) {
                    val a = artists.getJSONObject(0)
                    val area = a.optJSONObject("area")
                    val code = a.optString("country").ifBlank { area?.optString("iso-3166-1-codes")?.trim('[',']') ?: "" }
                    country = when (code.uppercase(Locale.ROOT)) {
                        "US" -> "آمریکا"; "GB" -> "بریتانیا"; "KR" -> "کره جنوبی"; "JP" -> "ژاپن"; "IR" -> "ایران"; "TR" -> "ترکیه"; "FR" -> "فرانسه"; "DE" -> "آلمان"; "CA" -> "کانادا"; "AU" -> "استرالیا"; "IN" -> "هند"; "IT" -> "ایتالیا"; "ES" -> "اسپانیا"; "BR" -> "برزیل"; "SE" -> "سوئد"; "RU" -> "روسیه"; "CN" -> "چین"; "MX" -> "مکزیک"; "NL" -> "هلند"; "IE" -> "ایرلند"; else -> if (code.isNotBlank()) Locale("", code).getDisplayCountry(Locale("fa")) else "نامشخص"
                    }
                }
                conn.disconnect()
            } catch (_: Exception) { }
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(country) }
        }
    }
}
