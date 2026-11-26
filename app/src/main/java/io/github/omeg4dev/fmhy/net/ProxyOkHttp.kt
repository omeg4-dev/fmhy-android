package io.github.omeg4dev.fmhy.net

import java.io.IOException
import java.io.InterruptedIOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

private const val PROXY_MIN_CONNECT_TIMEOUT_MS = 30_000
private const val PROXY_MIN_READ_TIMEOUT_MS = 60_000

class ProxyIOException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

fun buildProxySelector(proxyManager: ProxyManager): ProxySelector = object : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val endpoint = proxyManager.awaitEndpoint()
        return listOf(endpoint?.javaProxy ?: Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

fun buildProxyNetworkInterceptor(proxyManager: ProxyManager): Interceptor = Interceptor { chain ->
    val endpoint = try {
        proxyManager.awaitEndpoint()
    } catch (e: ProxyTimeoutException) {
        throw IOException(e.message ?: "Timed out waiting for proxy configuration", e)
    }
    val expectedProxy = endpoint?.javaProxy ?: Proxy.NO_PROXY
    val connection = chain.connection() ?: throw IOException("Missing connection")
    val routeProxy = connection.route().proxy
    if (routeProxy != expectedProxy) {
        try {
            connection.socket().close()
        } catch (_: Throwable) {}
        throw IOException("Connection proxy mismatch: expected $expectedProxy, got $routeProxy")
    }
    chain.proceed(chain.request())
}

fun buildProxyAppInterceptor(proxyManager: ProxyManager): Interceptor = Interceptor { chain ->
    val endpoint = try {
        proxyManager.awaitEndpoint()
    } catch (e: ProxyTimeoutException) {
        throw IOException(e.message ?: "Timed out waiting for proxy configuration", e)
    }
    val activeChain = if (endpoint != null) {
        var adjusted = chain
        if (adjusted.connectTimeoutMillis() in 1 until PROXY_MIN_CONNECT_TIMEOUT_MS) {
            adjusted = adjusted.withConnectTimeout(PROXY_MIN_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        if (adjusted.readTimeoutMillis() in 1 until PROXY_MIN_READ_TIMEOUT_MS) {
            adjusted = adjusted.withReadTimeout(PROXY_MIN_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        adjusted
    } else {
        chain
    }

    try {
        val response = activeChain.proceed(activeChain.request())
        if (endpoint != null) {
            wrapProxyResponseBody(response, endpoint, activeChain.call())
        } else {
            response
        }
    } catch (e: ProxyTimeoutException) {
        throw IOException(e.message ?: "Timed out waiting for proxy configuration", e)
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: ProxyIOException) {
        throw e
    } catch (e: IOException) {
        if (endpoint == null || activeChain.call().isCanceled()) {
            throw e
        }
        throw wrapProxyIOException(endpoint, e)
    }
}

private fun wrapProxyResponseBody(response: Response, endpoint: ProxyEndpoint, call: Call): Response {
    val delegate = response.body
    val wrapped = object : ResponseBody() {
        private val buffered by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    return try {
                        super.read(sink, byteCount)
                    } catch (e: InterruptedIOException) {
                        throw e
                    } catch (e: ProxyIOException) {
                        throw e
                    } catch (e: IOException) {
                        if (call.isCanceled()) throw e
                        throw wrapProxyIOException(endpoint, e)
                    }
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun source(): BufferedSource = buffered
        override fun close() = delegate.close()
    }
    return response.newBuilder().body(wrapped).build()
}

private fun wrapProxyIOException(endpoint: ProxyEndpoint, cause: IOException): ProxyIOException {
    val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
    return ProxyIOException("Proxy ${endpoint.label}: $detail", cause)
}
