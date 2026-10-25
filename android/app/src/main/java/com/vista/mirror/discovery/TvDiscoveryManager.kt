package com.vista.mirror.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class TvProtocol {
    DLNA_DMR, // Sony Bravia 2013, Samsung, LG, Panasonic, Philips
    WEBRTC_LAN, // Modern Android TV, Fire TV, Browser receiver
    GOOGLE_CAST // Chromecast, Google TV
}

data class DiscoveredTv(
    val id: String,
    val name: String,
    val model: String = "",
    val ip: String,
    val controlUrl: String = "",
    val protocol: TvProtocol = TvProtocol.DLNA_DMR,
    val lastSeenMs: Long = System.currentTimeMillis()
)

class TvDiscoveryManager(private val context: Context) {

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val tvMap = ConcurrentHashMap<String, DiscoveredTv>()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var isSearching = false

    suspend fun startDiscovery() = withContext(Dispatchers.IO) {
        if (isSearching) return@withContext
        isSearching = true

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("VistaTvDiscoveryLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        // Send SSDP M-SEARCH broadcast over Wi-Fi
        sendSsdpSearch()
        listenSsdpResponses()
    }

    fun stopDiscovery() {
        isSearching = false
        try {
            multicastLock?.release()
        } catch (_: Exception) {}
    }

    private suspend fun sendSsdpSearch() = withContext(Dispatchers.IO) {
        try {
            val ssdpQueries = listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:schemas-upnp-org:service:AVTransport:1",
                "ssdp:all"
            )

            val socket = DatagramSocket()
            val group = InetAddress.getByName("239.255.255.250")
            val port = 1900

            for (target in ssdpQueries) {
                val msg = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: $target\r\n\r\n"

                val bytes = msg.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, group, port)
                socket.send(packet)
            }
            socket.close()
        } catch (_: Exception) {}
    }

    private suspend fun listenSsdpResponses() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = 4000
            }
            val packet = DatagramPacket(buffer, buffer.size)

            val start = System.currentTimeMillis()
            while (isSearching && System.currentTimeMillis() - start < 15000) {
                try {
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress ?: ""
                    parseSsdpResponse(response, senderIp)
                } catch (_: Exception) {
                    // Socket timeout, repeat
                }
            }
        } catch (_: Exception) {
        } finally {
            socket?.close()
        }
    }

    private fun parseSsdpResponse(response: String, senderIp: String) {
        var location = ""
        var usn = ""
        val lines = response.split("\r\n", "\n")
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.startsWith("location:")) {
                location = line.substring(9).trim()
            } else if (lower.startsWith("usn:")) {
                usn = line.substring(4).trim()
            }
        }

        if (location.isNotEmpty()) {
            fetchDeviceDescription(location, senderIp, usn)
        }
    }

    private fun fetchDeviceDescription(url: String, ip: String, usn: String) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return
            val xml = response.body?.string() ?: return

            val parsed = parseUpnpXml(xml, url)
            if (parsed != null) {
                val id = parsed.udn.ifEmpty { usn.ifEmpty { "$ip:${parsed.friendlyName}" } }
                val tv = DiscoveredTv(
                    id = id,
                    name = cleanTvName(parsed.friendlyName),
                    model = parsed.modelName,
                    ip = ip,
                    controlUrl = parsed.avTransportControlUrl,
                    protocol = TvProtocol.DLNA_DMR
                )
                tvMap[id] = tv
                _discoveredTvs.value = tvMap.values.toList().sortedBy { it.name }
            }
        } catch (_: Exception) {}
    }

    private fun cleanTvName(raw: String): String {
        return raw.replace("[TV]", "").replace("Sony Bravia", "Sony Bravia").trim()
    }

    private data class ParsedUpnp(
        val friendlyName: String,
        val modelName: String,
        val udn: String,
        val avTransportControlUrl: String
    )

    private fun parseUpnpXml(xml: String, baseUrl: String): ParsedUpnp? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var friendlyName = ""
            var modelName = ""
            var udn = ""
            var currentTag = ""
            var insideAvTransport = false
            var controlUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = parser.name
                    if (currentTag == "service") {
                        insideAvTransport = false
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    val text = parser.text.trim()
                    when (currentTag) {
                        "friendlyName" -> if (friendlyName.isEmpty()) friendlyName = text
                        "modelName" -> if (modelName.isEmpty()) modelName = text
                        "UDN" -> if (udn.isEmpty()) udn = text
                        "serviceType" -> {
                            if (text.contains("AVTransport")) insideAvTransport = true
                        }
                        "controlURL" -> {
                            if (insideAvTransport && controlUrl.isEmpty()) {
                                controlUrl = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (friendlyName.isEmpty()) return null

            // Build full control URL if relative
            val fullControlUrl = if (controlUrl.startsWith("http")) {
                controlUrl
            } else {
                val uri = java.net.URI(baseUrl)
                "${uri.scheme}://${uri.host}:${uri.port}${if (controlUrl.startsWith("/")) "" else "/"}$controlUrl"
            }

            return ParsedUpnp(friendlyName, modelName, udn, fullControlUrl)
        } catch (e: Exception) {
            return null
        }
    }
}
