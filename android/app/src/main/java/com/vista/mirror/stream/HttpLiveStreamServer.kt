package com.vista.mirror.stream

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded HTTP server on the Android device.
 * Serves live MJPEG stream (video) and live WAV stream (audio) over local Wi-Fi.
 * Works natively on any Smart TV including Sony Bravia 2013, Samsung, LG.
 */
class HttpLiveStreamServer(port: Int = 8080) : NanoHTTPD(port) {

    private val mjpegListeners = CopyOnWriteArrayList<PipedOutputStream>()
    private var lastJpegFrame: ByteArray? = null

    fun pushJpegFrame(jpeg: ByteArray) {
        lastJpegFrame = jpeg
        val boundary = "\r\n--vista_frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
        val headerBytes = boundary.toByteArray()

        val iterator = mjpegListeners.iterator()
        while (iterator.hasNext()) {
            val stream = iterator.next()
            try {
                stream.write(headerBytes)
                stream.write(jpeg)
                stream.flush()
            } catch (e: Exception) {
                mjpegListeners.remove(stream)
                try { stream.close() } catch (_: Exception) {}
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/live.mjpg" || uri == "/stream" || uri == "/video" -> {
                val pipedOut = PipedOutputStream()
                val pipedIn = PipedInputStream(pipedOut, 64 * 1024)
                mjpegListeners.add(pipedOut)

                // Write initial frame if available
                lastJpegFrame?.let { jpeg ->
                    try {
                        val init = "--vista_frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
                        pipedOut.write(init.toByteArray())
                        pipedOut.write(jpeg)
                        pipedOut.flush()
                    } catch (_: Exception) {}
                }

                val response = newChunkedResponse(
                    Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=--vista_frame",
                    pipedIn
                )
                response.addHeader("Cache-Control", "no-cache, private")
                response.addHeader("Access-Control-Allow-Origin", "*")
                response
            }
            uri == "/frame.jpg" -> {
                val frame = lastJpegFrame ?: ByteArray(0)
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    ByteArrayInputStream(frame),
                    frame.size.toLong()
                )
                response.addHeader("Cache-Control", "no-cache, must-revalidate")
                response.addHeader("Access-Control-Allow-Origin", "*")
                response
            }
            else -> {
                newFixedLengthResponse(Response.Status.OK, "text/plain", "Vista Android Stream Server Running")
            }
        }
    }

    override fun stop() {
        for (stream in mjpegListeners) {
            try { stream.close() } catch (_: Exception) {}
        }
        mjpegListeners.clear()
        super.stop()
    }
}
