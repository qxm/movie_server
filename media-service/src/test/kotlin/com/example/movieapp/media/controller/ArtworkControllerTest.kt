package com.example.movieapp.media.controller

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

class ArtworkControllerTest {
    private lateinit var directory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        directory = Files.createTempDirectory("artwork-test-").toAbsolutePath().normalize()
    }

    @AfterEach
    fun removeTemporaryDirectory() {
        if (!::directory.isInitialized) return
        val temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
        check(directory.parent == temporaryRoot && directory.fileName.toString().startsWith("artwork-test-"))
        // Delete only this test's generated directory, without following symbolic links.
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
    private fun controller() = ArtworkController(directory.toString())
    private fun image(format: String): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output)
        output.toByteArray()
    }
    private fun file(bytes: ByteArray, contentType: String = "image/png", filename: String = "poster.png") =
        MockMultipartFile("file", filename, contentType, bytes)

    @ParameterizedTest
    @ValueSource(strings = ["png", "jpeg"])
    fun `upload stores and serves decodable images with generated names`(format: String) {
        val api = controller()
        val bytes = image(format)
        val url = api.upload(file(bytes)).url
        val filename = url.substringAfterLast('/')
        assertTrue(filename.matches(Regex("[a-f0-9-]{36}\\.(png|jpg)")))
        val response = api.get(filename)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(if (format == "png") MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG, response.headers.contentType)
        assertEquals("public, max-age=86400", response.headers.cacheControl)
        assertArrayEquals(bytes, response.body!!.inputStream.use { it.readBytes() })
    }

    @Test
    fun `actual content determines extension despite filename and header`() {
        val url = controller().upload(file(image("png"), "image/jpeg", "../../poster.jpg")).url
        assertTrue(url.endsWith(".png"))
        assertFalse(url.contains(".."))
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `same original filename never overwrites an existing poster`() {
        val api = controller()
        val first = api.upload(file(image("png"))).url
        val second = api.upload(file(image("png"))).url
        assertNotEquals(first, second)
        assertEquals(2L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `empty upload is rejected without creating a file`() {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().upload(file(byteArrayOf())) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `oversized upload is rejected without creating a file`() {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().upload(file(ByteArray(5 * 1024 * 1024 + 1))) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `fake image bytes are rejected even with valid header`() {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().upload(file("not an image".toByteArray())) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `decodable but unsupported gif is rejected`() {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().upload(file(image("gif"), "image/gif")) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @ParameterizedTest
    @ValueSource(strings = ["../secret.png", "poster.png", "", "../../secret", "00000000-0000-0000-0000-000000000000.svg"])
    fun `invalid filenames cannot access files`(filename: String) {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().get(filename) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `valid filename with missing file returns not found`() {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().get("${UUID.randomUUID()}.png") }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `valid png at exact size limit is accepted`() {
        val bytes = image("png").copyOf(5 * 1024 * 1024)
        val api = controller()
        val url = api.upload(file(bytes)).url
        assertEquals(bytes.size.toLong(), api.get(url.substringAfterLast('/')).body!!.contentLength())
    }

    @Test
    fun `truncated image is rejected without writing`() {
        val ex = assertThrows(ResponseStatusException::class.java) { controller().upload(file(image("png").copyOf(20))) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    private fun webp(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/artworks/$name.webp")).use { it.readBytes() }

    @ParameterizedTest
    @ValueSource(strings = ["lossy", "lossless"])
    fun `webp is decoded stored unchanged and served with correct content type`(name: String) {
        val bytes = webp(name)
        val api = controller()
        // Detection must use bytes, including when the client supplies misleading metadata.
        val url = api.upload(file(bytes, "image/jpeg", "poster.jpg")).url
        assertTrue(url.endsWith(".webp"))
        val response = api.get(url.substringAfterLast('/'))
        assertEquals(MediaType.parseMediaType("image/webp"), response.headers.contentType)
        assertEquals("public, max-age=86400", response.headers.cacheControl)
        assertArrayEquals(bytes, response.body!!.inputStream.use { it.readBytes() })
    }

    @Test
    fun `webp filename and header cannot disguise invalid image bytes`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            controller().upload(file("not an image".toByteArray(), "image/webp", "poster.webp"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @ParameterizedTest
    @ValueSource(strings = ["lossy", "lossless"])
    fun `truncated webp is rejected without writing a file`(name: String) {
        val ex = assertThrows(ResponseStatusException::class.java) {
            controller().upload(file(webp(name).copyOf(24), "image/webp", "poster.webp"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }
}
