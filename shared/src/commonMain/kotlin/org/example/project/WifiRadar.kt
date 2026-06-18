package org.example.project

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object C {
    val Bg         = Color(0xFF080C14)
    val Surface    = Color(0xFF0C1120)
    val Panel      = Color(0xFF101828)
    val Border     = Color(0xFF1C2A3E)
    val Radar      = Color(0xFF00FF9C)
    val Cyan       = Color(0xFF00D4FF)
    val Purple     = Color(0xFFAD72FF)
    val TxtPri     = Color(0xFFE2E8F0)
    val TxtSec     = Color(0xFF64748B)
    val TxtMut     = Color(0xFF334155)
    val SigEx      = Color(0xFF00FF9C)   // excellent  ≥75 %
    val SigGo      = Color(0xFF4ADE80)   // good       ≥50 %
    val SigFa      = Color(0xFFFBBF24)   // fair       ≥25 %
    val SigPo      = Color(0xFFF87171)   // poor        <25 %
}

// ─────────────────────────────────────────────────────────
//  DATA MODEL
// ─────────────────────────────────────────────────────────

data class WifiNetwork(
    val ssid     : String,
    val bssid    : String,
    val signal   : Int,      // 0-100 %
    val channel  : Int,
    val band     : String,   // "2.4 GHz" | "5 GHz"
    val security : String
) {
    val dbm      : Int   get() = -100 + signal / 2
    val color    : Color get() = when {
        signal >= 75 -> C.SigEx
        signal >= 50 -> C.SigGo
        signal >= 25 -> C.SigFa
        else         -> C.SigPo
    }
    /** Stable angle on the radar derived from BSSID hash */
    val radarAngle: Float get() {
        val h = bssid.replace(":", "").toLongOrNull(16) ?: bssid.hashCode().toLong()
        return ((h and 0xFFFFFFFFL) % 360L).toFloat()
    }
}

// ─────────────────────────────────────────────────────────
//  WIFI SCANNER
// ─────────────────────────────────────────────────────────

object WifiScanner {

    fun scan(): List<WifiNetwork> = try {
        when {
            os("win")  -> scanWindows()
            os("mac")  -> scanMac()
            else       -> scanLinux()
        }
    } catch (_: Exception) { emptyList() }

    private fun os(s: String) = System.getProperty("os.name").lowercase().contains(s)

    private fun exec(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    // ── Windows ──────────────────────────────────────────
    private fun scanWindows(): List<WifiNetwork> {
        val raw = exec("netsh", "wlan", "show", "networks", "mode=bssid")
        val nets = mutableListOf<WifiNetwork>()
        val blocks = raw.split(Regex("(?=SSID \\d+ :)")).drop(1)
        for (b in blocks) {
            val ls = b.lines()
            val ssid     = ls.firstOrNull()?.substringAfter(":")?.trim() ?: continue
            val bssid    = ls.find { it.matches(Regex(".*BSSID \\d+.*:.*")) }
                ?.substringAfter("BSSID")?.substringAfter(":")?.trim() ?: "00:00:00:00:00:00"
            val signal   = ls.find { it.trim().startsWith("Signal") }
                ?.substringAfter(":")?.trim()?.removeSuffix("%")?.toIntOrNull() ?: 0
            val channel  = ls.find { it.trim().startsWith("Channel") }
                ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
            val security = ls.find { it.trim().startsWith("Authentication") }
                ?.substringAfter(":")?.trim() ?: "Open"
            nets += WifiNetwork(ssid, bssid, signal, channel, if (channel > 14) "5 GHz" else "2.4 GHz", security)
        }
        return nets
    }

    // ── macOS ────────────────────────────────────────────
    private fun scanMac(): List<WifiNetwork> {
        val airport = "/System/Library/PrivateFrameworks/Apple80211.framework" +
                "/Versions/Current/Resources/airport"
        val raw = exec(airport, "-s")
        return raw.lines().drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 3) return@mapNotNull null
            val ssid    = p[0]
            val bssid   = p[1]
            val rssi    = p[2].toIntOrNull() ?: -100
            val channel = p.getOrNull(3)?.substringBefore(",")?.toIntOrNull() ?: 0
            val sec     = if (p.size > 6) p.drop(6).joinToString(" ") else "Open"
            val sig     = (rssi + 100).coerceIn(0, 100)
            WifiNetwork(ssid, bssid, sig, channel, if (channel > 14) "5 GHz" else "2.4 GHz", sec)
        }
    }

    // ── Linux ────────────────────────────────────────────
    private fun scanLinux(): List<WifiNetwork> {
        val raw = exec("nmcli", "-f", "SSID,BSSID,SIGNAL,CHAN,SECURITY", "-t", "dev", "wifi")
        return raw.lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(":")
            if (p.size < 5) return@mapNotNull null
            val ch  = p[3].toIntOrNull() ?: 0
            WifiNetwork(p[0], p[1], p[2].toIntOrNull() ?: 0, ch,
                if (ch > 14) "5 GHz" else "2.4 GHz", p[4])
        }
    }
}

// ─────────────────────────────────────────────────────────
//  RADAR CANVAS
// ─────────────────────────────────────────────────────────

@Composable
fun RadarView(
    networks : List<WifiNetwork>,
    selected : WifiNetwork?,
    modifier : Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "sweep")
    val sweep by inf.animateFloat(
        initialValue   = -90f,
        targetValue    = 270f,
        animationSpec  = infiniteRepeatable(
            tween(4000, easing = LinearEasing), RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = modifier) {
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val R  = minOf(size.width, size.height) / 2f * 0.88f

        // ── background glow ──
        drawCircle(
            brush = Brush.radialGradient(
                listOf(C.Radar.copy(.06f), Color.Transparent),
                center = Offset(cx, cy), radius = R
            ),
            radius = R, center = Offset(cx, cy)
        )

        // ── range rings ──
        for (i in 1..4) {
            val r = R * i / 4f
            drawCircle(
                color  = C.Radar.copy(alpha = 0.10f + 0.04f * i),
                radius = r,
                center = Offset(cx, cy),
                style  = Stroke(if (i == 4) 1.5f else 0.7f)
            )
        }

        // ── crosshairs ──
        val hairColor = C.Radar.copy(.18f)
        drawLine(hairColor, Offset(cx - R, cy), Offset(cx + R, cy), 0.8f)
        drawLine(hairColor, Offset(cx, cy - R), Offset(cx, cy + R), 0.8f)

        // ── diagonal ticks ──
        for (deg in listOf(45f, 135f, 225f, 315f)) {
            val rad = Math.toRadians(deg.toDouble())
            drawLine(
                hairColor.copy(.10f),
                Offset(cx, cy),
                Offset(cx + R * cos(rad).toFloat(), cy + R * sin(rad).toFloat()),
                0.6f
            )
        }

        // ── sweep trail (gradient arc) ──
        val trailLen = 55f
        val steps    = 30
        for (s in 0 until steps) {
            val startA = sweep - trailLen * (s + 1) / steps
            val alpha  = (1f - s.toFloat() / steps) * 0.22f
            drawArc(
                color      = C.Radar.copy(alpha),
                startAngle = startA,
                sweepAngle = trailLen / steps,
                useCenter  = true,
                topLeft    = Offset(cx - R, cy - R),
                size       = Size(R * 2, R * 2)
            )
        }

        // ── sweep line ──
        val sweepRad = Math.toRadians(sweep.toDouble())
        drawLine(
            color       = C.Radar.copy(.95f),
            start       = Offset(cx, cy),
            end         = Offset(cx + R * cos(sweepRad).toFloat(), cy + R * sin(sweepRad).toFloat()),
            strokeWidth = 1.8f
        )

        // ── network blips ──
        networks.forEach { net ->
            val angleDiff = ((sweep - net.radarAngle + 360f) % 360f)
            val alpha = when {
                angleDiff < 5f  -> 1f
                angleDiff < 80f -> 1f - (angleDiff - 5f) / 75f
                else            -> 0.18f
            }
            val rad  = Math.toRadians(net.radarAngle.toDouble())
            val dist = R * (1f - net.signal / 100f * 0.82f)
            val bx   = cx + dist * cos(rad).toFloat()
            val by   = cy + dist * sin(rad).toFloat()

            // glow halo
            drawCircle(net.color.copy(alpha * .25f), 14f, Offset(bx, by))
            // dot
            val dotR = if (net == selected) 7f else 5f
            drawCircle(net.color.copy(alpha), dotR, Offset(bx, by))
            // selection ring
            if (net == selected) {
                drawCircle(net.color.copy(.85f), 13f, Offset(bx, by), style = Stroke(1.5f))
            }
        }

        // ── center pip ──
        drawCircle(C.Radar, 4f, Offset(cx, cy))
        drawCircle(C.Radar.copy(.25f), 11f, Offset(cx, cy), style = Stroke(1f))
    }
}

// ─────────────────────────────────────────────────────────
//  SIGNAL BARS WIDGET
// ─────────────────────────────────────────────────────────

@Composable
fun SignalBars(signal: Int, color: Color, modifier: Modifier = Modifier) {
    val bars = 4
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..bars) {
            val filled = signal >= (i * 100 / bars) - (100 / bars / 2)
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (filled) color else C.Border)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
//  NETWORK LIST
// ─────────────────────────────────────────────────────────

@Composable
fun NetworkList(
    networks : List<WifiNetwork>,
    selected : WifiNetwork?,
    onSelect : (WifiNetwork) -> Unit,
    modifier : Modifier = Modifier
) {
    Column(modifier = modifier) {
        // header row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SSID",     Modifier.weight(2.5f), style = headerStyle())
            Text("SIGNAL",   Modifier.weight(1.2f), style = headerStyle())
            Text("CH",       Modifier.weight(0.7f), style = headerStyle())
            Text("BAND",     Modifier.weight(1f),   style = headerStyle())
            Text("SECURITY", Modifier.weight(2f),   style = headerStyle())
        }
        HorizontalDivider(color = C.Border, thickness = 0.5.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(networks.sortedByDescending { it.signal }) { net ->
                val isSelected = net == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(net) }
                        .background(if (isSelected) net.color.copy(.08f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SSID
                    Row(Modifier.weight(2.5f), verticalAlignment = Alignment.CenterVertically) {

                        Spacer(Modifier.width(6.dp))
                        Text(
                            net.ssid.ifBlank { "(hidden)" },
                            color   = if (isSelected) net.color else C.TxtPri,
                            fontSize= 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Signal %
                    Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                        SignalBars(net.signal, net.color)
                        Spacer(Modifier.width(5.dp))
                        Text("${net.signal}%", color = net.color, fontSize = 12.sp)
                    }
                    // Channel
                    Text(net.channel.toString(), Modifier.weight(0.7f), color = C.TxtSec, fontSize = 12.sp)
                    // Band
                    Text(net.band, Modifier.weight(1f), color = C.TxtSec, fontSize = 12.sp)
                    // Security
                    Text(
                        net.security.take(20),
                        Modifier.weight(2f),
                        color = C.TxtMut,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

            }
        }
    }
}

@Composable private fun headerStyle() = LocalTextStyle.current.copy(
    color      = C.TxtSec,
    fontSize   = 10.sp,
    fontWeight = FontWeight.Bold,
    fontFamily = FontFamily.Monospace
)

// ─────────────────────────────────────────────────────────
//  CHANNEL CONGESTION CHART
// ─────────────────────────────────────────────────────────

@Composable
fun ChannelChart(networks: List<WifiNetwork>, modifier: Modifier = Modifier) {
    val byChannel = networks.groupBy { it.channel }
    val maxCount  = (byChannel.values.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)

    // separate 2.4 and 5 GHz
    val ch24 = (1..13).map { ch -> ch to (byChannel[ch]?.size ?: 0) }
    val ch5  = listOf(36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140,149,153,157,161,165)
        .map { ch -> ch to (byChannel[ch]?.size ?: 0) }

    Column(modifier = modifier.padding(12.dp)) {
        Text("CHANNEL CONGESTION", color = C.TxtSec, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Text("2.4 GHz", color = C.Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ChannelBars(ch24.filter { it.second > 0 || it.first in listOf(1,6,11) }, maxCount)

        Spacer(Modifier.height(14.dp))
        Text("5 GHz", color = C.Purple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ChannelBars(ch5.filter { it.second > 0 }, maxCount, color = C.Purple)
    }
}

@Composable
private fun ChannelBars(
    data     : List<Pair<Int,Int>>,
    maxCount : Int,
    color    : Color = C.Cyan
) {
    if (data.isEmpty()) {
        Text("No networks detected", color = C.TxtMut, fontSize = 11.sp)
        return
    }
    Row(
        Modifier.fillMaxWidth().height(60.dp),
        verticalAlignment   = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        data.forEach { (ch, count) ->
            val frac = count.toFloat() / maxCount
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (count > 0) Text(count.toString(), color = color, fontSize = 8.sp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(frac.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                Brush.verticalGradient(listOf(color, color.copy(.4f)))
                            )
                    )
                }
                Text(ch.toString(), color = C.TxtMut, fontSize = 8.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  SIGNAL HISTORY LINE CHART
// ─────────────────────────────────────────────────────────

@Composable
fun SignalHistory(
    networks : List<WifiNetwork>,
    history  : Map<String, List<Pair<Long, Int>>>,
    selected : WifiNetwork?,
    modifier : Modifier = Modifier
) {
    val toShow = if (selected != null) {
        listOf(selected).mapNotNull { n -> history[n.bssid]?.let { n to it } }
    } else {
        networks.take(5).mapNotNull { n -> history[n.bssid]?.let { n to it } }
    }

    Column(modifier = modifier.padding(12.dp)) {
        Text("SIGNAL HISTORY", color = C.TxtSec, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (toShow.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Scanning…", color = C.TxtMut, fontSize = 12.sp)
            }
            return
        }

        Canvas(Modifier.fillMaxSize()) {
            val pw = size.width
            val ph = size.height
            val pad = 20f

            // grid lines
            for (i in 0..4) {
                val y = pad + (ph - 2 * pad) * i / 4f
                drawLine(C.Border.copy(.6f), Offset(pad, y), Offset(pw - pad, y), 0.5f)
            }

            // y-axis labels helper
            // (drawn via text on composable layer)

            // lines per network
            toShow.forEachIndexed { idx, (net, pts) ->
                if (pts.size < 2) return@forEachIndexed
                val minT = pts.first().first
                val maxT = pts.last().first
                val tRange = (maxT - minT).coerceAtLeast(1L)

                val path = Path()
                pts.forEachIndexed { i, (t, sig) ->
                    val x = pad + (t - minT).toFloat() / tRange * (pw - 2 * pad)
                    val y = pad + (1f - sig / 100f) * (ph - 2 * pad)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                val lineColor = net.color.copy(if (selected != null) 1f else 0.7f)
                drawPath(path, lineColor, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // last dot
                val last  = pts.last()
                val lastX = pad + (last.first - minT).toFloat() / tRange * (pw - 2 * pad)
                val lastY = pad + (1f - last.second / 100f) * (ph - 2 * pad)
                drawCircle(lineColor, 4f, Offset(lastX, lastY))
            }
        }

        // legend
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            toShow.forEach { (net, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp, 2.dp).background(net.color, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        net.ssid.take(12).ifBlank { net.bssid.takeLast(5) },
                        color = net.color, fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  PANEL WRAPPER
// ─────────────────────────────────────────────────────────

@Composable
fun Panel(
    title    : String,
    modifier : Modifier = Modifier,
    content  : @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(C.Panel)
            .border(0.5.dp, C.Border, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(C.Radar))
            Spacer(Modifier.width(8.dp))
            Text(title, color = C.TxtSec, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
        }
        HorizontalDivider(Modifier, thickness = 0.5.dp, color = C.Border)
        content()
    }
}

// ─────────────────────────────────────────────────────────
//  DETAIL CARD (selected network)
// ─────────────────────────────────────────────────────────

@Composable
fun DetailCard(net: WifiNetwork, modifier: Modifier = Modifier, neco: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(net.color.copy(.08f))
            .border(0.5.dp, net.color.copy(.3f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(net.ssid.ifBlank { "(hidden SSID)" }, color = net.color,
            fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val rows = listOf(
            "BSSID"    to net.bssid,
            "Signal"   to "${net.signal}% (${net.dbm} dBm)",
            "Channel"  to net.channel.toString(),
            "Band"     to net.band,
            "Security" to net.security
        )
        rows.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("$k:", Modifier.width(72.dp), color = C.TxtSec, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
                Text(v, color = C.TxtPri, fontSize = 11.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  MAIN APP
// ─────────────────────────────────────────────────────────

@Composable
fun WifiRadarApp() {
    val networks  = remember { mutableStateListOf<WifiNetwork>() }
    val history   = remember { mutableStateMapOf<String, MutableList<Pair<Long, Int>>>() }
    var selected  by remember { mutableStateOf<WifiNetwork?>(null) }
    var scanning  by remember { mutableStateOf(false) }
    var lastScan  by remember { mutableStateOf("—") }
    var scanCount by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    fun doScan() {
        scope.launch {
            scanning = true
            val found = withContext(Dispatchers.IO) { WifiScanner.scan() }
            val now   = System.currentTimeMillis()
            // upsert history
            found.forEach { net ->
                history.getOrPut(net.bssid) { mutableListOf() }.also {
                    it.add(now to net.signal)
                    if (it.size > 60) it.removeAt(0)
                }
            }
            networks.clear()
            networks.addAll(found)
            lastScan  = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
            scanCount++
            scanning  = false
            // keep selected in sync
            selected = selected?.let { s -> networks.find { it.bssid == s.bssid } }
        }
    }

    // Auto-scan every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            doScan()
            delay(10_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = C.Bg)) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {

            Column(Modifier.fillMaxSize().padding(16.dp)) {

                // ── Top bar ────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically
                ) {
                    // Title
                    Column {
                        Text(
                            "WiFi Radar Analyzer",
                            color      = C.TxtPri,
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${networks.size} network${if (networks.size != 1) "s" else ""} • last scan $lastScan",
                            color    = C.TxtSec,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Scan counter badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.Border)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("SCAN #$scanCount", color = C.Radar, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace)
                    }

                    Spacer(Modifier.width(10.dp))

                    // Refresh button
                    Button(
                        onClick = { doScan() },
                        enabled = !scanning,
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = C.Radar.copy(.15f),
                            contentColor   = C.Radar
                        ),
                        shape   = RoundedCornerShape(8.dp)
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp), color = C.Radar, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Scanning…", fontSize = 12.sp)
                        } else {
//                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Now", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Main body ──────────────────────────────────
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    // LEFT COLUMN: radar
                    Column(
                        Modifier.width(360.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Panel(
                            "RADAR",
                            Modifier.fillMaxWidth().aspectRatio(1f)
                        ) {
                            RadarView(networks, selected, Modifier.fillMaxSize().padding(8.dp))
                        }

                        // Detail card (shows when a network is selected)
                        if (selected != null) {
                            DetailCard(selected!!, Modifier.fillMaxWidth())
                        } else {
                            // Stats summary
                            Panel("OVERVIEW", Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatBox("Networks",  networks.size.toString(),             C.Cyan)
                                    StatBox("2.4 GHz",   networks.count { it.band == "2.4 GHz" }.toString(), C.SigGo)
                                    StatBox("5 GHz",     networks.count { it.band == "5 GHz"   }.toString(), C.Purple)
                                    val best = networks.maxByOrNull { it.signal }
                                    StatBox("Best", if (best != null) "${best.signal}%" else "—", best?.color ?: C.TxtSec)
                                }
                            }
                        }
                    }

                    // RIGHT COLUMN: list + charts
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Network list
                        Panel("NETWORKS — click a row to select", Modifier.weight(1.2f).fillMaxWidth()) {
                            if (networks.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No networks found. Try Scan Now.", color = C.TxtMut)
                                }
                            } else {
                                NetworkList(networks, selected, { selected = it }, Modifier.fillMaxSize())
                            }
                        }

                        // Bottom row: channel chart + signal history
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Panel("CHANNELS", Modifier.weight(1f).fillMaxSize()) {
                                ChannelChart(networks, Modifier.fillMaxSize())
                            }
                            Panel("SIGNAL HISTORY", Modifier.weight(1f).fillMaxSize()) {
                                SignalHistory(networks, history, selected, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  STAT BOX
// ─────────────────────────────────────────────────────────

@Composable
fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color,    fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.TxtSec, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}