package org.jellycopter.discovery

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MockResponse {
    var responseCode: Int = 200
        private set
    val headers: MutableMap<String, String> = mutableMapOf("Content-Type" to "application/json; charset=utf-8")
    var body: String = ""
        private set

    fun setResponseCode(code: Int): MockResponse {
        this.responseCode = code
        return this
    }

    fun setBody(body: String): MockResponse {
        this.body = body
        return this
    }

    fun addHeader(name: String, value: String): MockResponse {
        this.headers[name] = value
        return this
    }
}

data class RecordedRequest(
    val method: String,
    val path: String,
    val headers: Map<String, List<String>>,
    val body: String
) {
    fun getHeader(name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }
}

class MockWebServer : Closeable {
    private val serverSocket: ServerSocket = ServerSocket(0)
    val port: Int = serverSocket.localPort
    private val responseQueue = ConcurrentLinkedQueue<MockResponse>()
    private val requestQueue: BlockingQueue<RecordedRequest> = LinkedBlockingQueue()
    private val isRunning = AtomicBoolean(true)
    private val threadPool = Executors.newCachedThreadPool()

    init {
        threadPool.execute {
            while (isRunning.get() && !serverSocket.isClosed) {
                try {
                    val clientSocket = serverSocket.accept()
                    threadPool.execute { handleClient(clientSocket) }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun url(path: String = "/"): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "http://127.0.0.1:$port$normalizedPath"
    }

    fun enqueue(response: MockResponse) {
        responseQueue.add(response)
    }

    fun takeRequest(timeoutMs: Long = 5000): RecordedRequest? {
        return requestQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    val requestCount: Int get() = requestQueue.size

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val firstLine = reader.readLine() ?: return
                val parts = firstLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                val headers = mutableMapOf<String, MutableList<String>>()
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val name = line.substring(0, colonIdx).trim()
                        val value = line.substring(colonIdx + 1).trim()
                        headers.getOrPut(name) { mutableListOf() }.add(value)
                        if (name.equals("Content-Length", ignoreCase = true)) {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                }

                val body = if (contentLength > 0) {
                    val chars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = reader.read(chars, readTotal, contentLength - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    String(chars, 0, readTotal)
                } else {
                    ""
                }

                val recorded = RecordedRequest(method, path, headers, body)
                requestQueue.add(recorded)

                val mockResponse = responseQueue.poll() ?: MockResponse()
                    .setResponseCode(404)
                    .setBody("{\"error\": \"No mock response queued for $path\"}")

                val responseBytes = mockResponse.body.toByteArray(StandardCharsets.UTF_8)
                val out: OutputStream = s.getOutputStream()
                val statusMessage = when (mockResponse.responseCode) {
                    200 -> "OK"
                    201 -> "Created"
                    403 -> "Forbidden"
                    404 -> "Not Found"
                    409 -> "Conflict"
                    500 -> "Internal Server Error"
                    else -> "Response"
                }

                val headerSb = StringBuilder()
                headerSb.append("HTTP/1.1 ${mockResponse.responseCode} $statusMessage\r\n")
                if (!mockResponse.headers.containsKey("Content-Length")) {
                    mockResponse.headers["Content-Length"] = responseBytes.size.toString()
                }
                for ((k, v) in mockResponse.headers) {
                    headerSb.append("$k: $v\r\n")
                }
                headerSb.append("Connection: close\r\n\r\n")

                out.write(headerSb.toString().toByteArray(StandardCharsets.UTF_8))
                if (responseBytes.isNotEmpty()) {
                    out.write(responseBytes)
                }
                out.flush()
            }
        } catch (_: Exception) {
            // Socket closed
        }
    }

    override fun close() {
        shutdown()
    }

    fun shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                serverSocket.close()
            } catch (_: Exception) {}
            threadPool.shutdownNow()
        }
    }
}
