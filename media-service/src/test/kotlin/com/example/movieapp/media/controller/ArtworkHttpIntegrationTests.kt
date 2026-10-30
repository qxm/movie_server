package com.example.movieapp.media.controller

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtworkHttpIntegrationTests {
    @Autowired lateinit var http: TestRestTemplate
    @Autowired lateinit var mapper: com.fasterxml.jackson.databind.ObjectMapper

    companion object {
        private val temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
        private val directory = Files.createTempDirectory(temporaryRoot, "media-http-test-")

        @JvmStatic @DynamicPropertySource
        fun storage(registry: DynamicPropertyRegistry) {
            registry.add("app.artwork.upload-dir") { directory.toString() }
        }

        @JvmStatic @AfterAll
        fun cleanup() {
            check(directory.parent == temporaryRoot && directory.fileName.toString().startsWith("media-http-test-"))
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    private fun upload(bytes: ByteArray): ResponseEntity<String> {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", object : ByteArrayResource(bytes) {
            override fun getFilename() = "poster.jpg"
        })
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        return http.postForEntity("/api/artworks", HttpEntity(body, headers), String::class.java)
    }

    @Test
    fun `standalone service uploads and serves a PNG over HTTP`() {
        val bytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
        val uploaded = upload(bytes)
        assertEquals(HttpStatus.OK, uploaded.statusCode)
        val url = mapper.readTree(uploaded.body!!).get("url").asText()
        assertTrue(url.matches(Regex("/api/artworks/[a-f0-9-]{36}\\.png")))
        val response = http.getForEntity(url, ByteArray::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.IMAGE_PNG, response.headers.contentType)
        assertEquals("public, max-age=86400", response.headers.cacheControl)
        assertArrayEquals(bytes, response.body)
    }

    @Test
    fun `standalone service discovers WebP decoder and serves correct MIME type`() {
        val bytes = javaClass.getResourceAsStream("/artworks/lossless.webp")!!.use { it.readBytes() }
        val uploaded = upload(bytes)
        assertEquals(HttpStatus.OK, uploaded.statusCode)
        val url = mapper.readTree(uploaded.body!!).get("url").asText()
        assertTrue(url.endsWith(".webp"))
        val response = http.getForEntity(url, ByteArray::class.java)
        assertEquals(MediaType.parseMediaType("image/webp"), response.headers.contentType)
        assertArrayEquals(bytes, response.body)
    }

    @Test
    fun `missing file part is rejected`() {
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        val response = http.postForEntity("/api/artworks", HttpEntity(LinkedMultiValueMap<String, Any>(), headers), String::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `fake image is rejected over HTTP`() {
        assertEquals(HttpStatus.BAD_REQUEST, upload("not an image".toByteArray()).statusCode)
    }

    @Test
    fun `multipart configuration enforces five megabyte upload limit`() {
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, upload(ByteArray(5 * 1024 * 1024 + 1)).statusCode)
    }

    @Test
    fun `missing artwork returns HTTP not found`() {
        val response = http.getForEntity("/api/artworks/${UUID.randomUUID()}.png", String::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
