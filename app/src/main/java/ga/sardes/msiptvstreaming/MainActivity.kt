    package ga.sardes.msiptvstreaming

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import ga.sardes.msiptvstreaming.ui.theme.MSIPTVStreamingTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MSIPTVStreamingTheme {

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar( title = { Text("MS IPTV Streaming")})}
                ) { innerPadding ->
                    var channels by remember { mutableStateOf(emptyList<TVChannel>()) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        scope.launch {
                            val m3uContent = downloadM3UFile("https://iptv-org.github.io/iptv/countries/ga.m3u")
                            channels = parseM3UFile(m3uContent)
                        }
                    }

                    TVChannelsScreen(innerPadding, channels)
                }
            }
        }
    }
}

@Composable
fun TVChannelsScreen(contentPadding: PaddingValues, channels: List<TVChannel>) {
    var selectedChannel by remember { mutableStateOf<TVChannel?>(null) }

    if (selectedChannel == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            items(channels) { channel ->
                ChannelItem(channel, onClick = { selectedChannel = channel })
            }
        }
    } else {
        BackHandler { selectedChannel = null }
        VideoPlayer(selectedChannel!!.streamUrl)
    }
}

@Composable
fun ChannelItem(channel: TVChannel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

suspend fun downloadM3UFile(url: String): String {
    val client = HttpClient(CIO)
    return try {
        client.get(urlString = url).body()
    } catch (e: Exception) {
        e.printStackTrace().toString()
    }
}

data class TVChannel(
    val name: String,
    val logoUrl: String?,
    val streamUrl: String
)

fun parseM3UFile(content: String): List<TVChannel> {
    val channels = mutableListOf<TVChannel>()
    val lines = content.lines()
    var currentName: String? = null
    var currentLogo: String? = null

    for (line in lines) {
        when {
            line.startsWith("#EXTINF") -> {
                val nameRegex = Regex(""",(.+)$""")
                val logoRegex = Regex("""tvg-logo="([^"]*)"""")

                currentName = nameRegex.find(line)?.groupValues?.get(1)
                currentLogo = logoRegex.find(line)?.groupValues?.get(1)
            }
            line.startsWith("http") -> {
                currentName?.let {
                    channels.add(TVChannel(name = it, logoUrl = currentLogo, streamUrl = line))
                }
                currentName = null
                currentLogo = null
            }
        }
    }
    return channels
}

@Composable
fun VideoPlayer(streamUrl: String) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            setMediaItem(mediaItem)
            prepare()
        }
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}