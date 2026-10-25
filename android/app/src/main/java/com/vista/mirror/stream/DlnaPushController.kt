package com.vista.mirror.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends UPnP/DLNA SOAP commands directly to Sony Bravia 2013, Samsung, LG Media Renderers
 * to start playing the phone's live screen & audio mirror stream automatically.
 */
class DlnaPushController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun playStreamOnTv(controlUrl: String, streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (controlUrl.isEmpty()) return@withContext false

        val setUriSuccess = sendSoapAction(
            controlUrl = controlUrl,
            action = "SetAVTransportURI",
            soapBody = """
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    <InstanceID>0</InstanceID>
                    <CurrentURI>$streamUrl</CurrentURI>
                    <CurrentURIMetaData></CurrentURIMetaData>
                </u:SetAVTransportURI>
            """.trimIndent()
        )

        if (!setUriSuccess) return@withContext false

        // Send Play action
        sendSoapAction(
            controlUrl = controlUrl,
            action = "Play",
            soapBody = """
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    <InstanceID>0</InstanceID>
                    <Speed>1</Speed>
                </u:Play>
            """.trimIndent()
        )
    }

    suspend fun stopStreamOnTv(controlUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (controlUrl.isEmpty()) return@withContext false
        sendSoapAction(
            controlUrl = controlUrl,
            action = "Stop",
            soapBody = """
                <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    <InstanceID>0</InstanceID>
                </u:Stop>
            """.trimIndent()
        )
    }

    private fun sendSoapAction(controlUrl: String, action: String, soapBody: String): Boolean {
        return try {
            val xml = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        $soapBody
    </s:Body>
</s:Envelope>"""

            val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
            val body = xml.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }
}
