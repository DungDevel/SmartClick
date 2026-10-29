package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

class SonyBraviaDriverTest {

    @Test
    fun `port 80 with router 404 HTML is not Sony`() = runTest {
        val result = fingerprint(404, "<html>router</html>", openPorts = setOf(80))

        assertNull(result)
    }

    @Test
    fun `HTTP 200 with non JSON body is not Sony`() = runTest {
        assertNull(fingerprint(200, "not JSON", openPorts = setOf(80)))
    }

    @Test
    fun `HTTP 200 JSON with mismatched id is not Sony`() = runTest {
        // Fixture dựng từ tài liệu Sony, chưa bắt từ TV thật.
        val body = """{"result":[{"interfaceVersion":"5.0.0"}],"id":34}"""

        assertNull(fingerprint(200, body))
    }

    @Test
    fun `HTTP 200 JSON without result or error is not Sony`() = runTest {
        // Fixture dựng từ tài liệu Sony, chưa bắt từ TV thật.
        assertNull(fingerprint(200, """{"id":33}"""))
    }

    @Test
    fun `connection failure is not Sony despite ports and Sony protocol hint`() = runTest {
        val driver = SonyBraviaDriver(
            OkHttpClient.Builder()
                .addInterceptor { throw IOException("connection timed out") }
                .build()
        )
        val candidate = RawCandidate(
            ipAddress = "192.0.2.10",
            protocolHint = TvProtocol.SONY_BRAVIA,
            openPorts = setOf(80, 52323)
        )

        assertNull(driver.fingerprint(candidate))
    }

    @Test
    fun `documented interface information response is HIGH`() = runTest {
        // Fixture dựng từ tài liệu Sony, chưa bắt từ TV thật.
        val body = """{"result":[{"interfaceVersion":"5.0.0"}],"id":33}"""

        val result = fingerprint(200, body)

        assertNotNull(result)
        assertEquals(TvProtocol.SONY_BRAVIA, result?.protocol)
        assertEquals(Fingerprint.Confidence.HIGH, result?.confidence)
    }

    @Test
    fun `documented unauthorized response is MEDIUM`() = runTest {
        // Fixture dựng từ tài liệu Sony, chưa bắt từ TV thật.
        val body = """{"error":[401,"Unauthorized"],"id":33}"""

        val result = fingerprint(200, body)

        assertNotNull(result)
        assertEquals(Fingerprint.Confidence.MEDIUM, result?.confidence)
    }

    @Test
    fun `request asks for documented interface information with id 33`() = runTest {
        var request: okhttp3.Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                request = chain.request()
                jsonResponse(chain.request(), 200, """{"result":[{"interfaceVersion":"5.0.0"}],"id":33}""")
            }
            .build()

        SonyBraviaDriver(client).fingerprint(RawCandidate("192.0.2.10"))

        assertEquals("/sony/system", request?.url?.encodedPath)
        val requestBody = okio.Buffer().also { request?.body?.writeTo(it) }.readUtf8()
        assertEquals(
            """{"method":"getInterfaceInformation","id":33,"params":[],"version":"1.0"}""",
            requestBody
        )
    }

    private suspend fun fingerprint(
        status: Int,
        body: String,
        openPorts: Set<Int> = emptySet()
    ) = SonyBraviaDriver(
        OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain.request(), status, body) }
            .build()
    ).fingerprint(RawCandidate("192.0.2.10", openPorts = openPorts))

    private fun jsonResponse(request: okhttp3.Request, status: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message(if (status in 200..299) "OK" else "Not Found")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
