package pt.aguiarvieira.xmuks.core.network

import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.GzipSource
import okio.buffer
import okio.source

/**
 * Asks for zstd (then gzip) and decodes the body. gomuks compresses `/sse` and `/exec` itself;
 * zstd is ~5× smaller than the raw JSON and cheaper to decode than deflate.
 *
 * Setting Accept-Encoding by hand turns off OkHttp's transparent gzip, so gzip is decoded here too.
 * The zstd decoder hands out bytes as soon as a block completes — required for `/sse`, whose single
 * zstd frame never ends and is flushed once per event.
 */
class CompressionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain
                .request()
                .newBuilder()
                .header("Accept-Encoding", "zstd, gzip")
                .build()
        val response = chain.proceed(request)
        val encoding = response.header("Content-Encoding")?.lowercase() ?: return response
        val body = response.body
        val decoded =
            when (encoding) {
                "zstd" -> ZstdInputStreamNoFinalizer(body.byteStream()).source().buffer()
                "gzip" -> GzipSource(body.source()).buffer()
                else -> return response
            }
        return response
            .newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(decoded.asResponseBody(body.contentType(), -1))
            .build()
    }
}
