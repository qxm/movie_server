package com.example.movieapp.movie.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

@RestController
@RequestMapping("/api/artworks")
class ArtworkController(
    @Value("\${app.artwork.upload-dir:./uploads/artworks}") uploadDir: String
) {
    private val storage: Path = Path.of(uploadDir).toAbsolutePath().normalize().also {
        Files.createDirectories(it)
    }

    data class UploadResponse(val url: String)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): UploadResponse {
        if (file.isEmpty || file.size > 5L * 1024 * 1024) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 1–5 MB or smaller")
        }
        // Inspect actual image bytes; do not trust the extension or Content-Type header.
        val format = try {
            file.inputStream.use { stream ->
                val imageInput = ImageIO.createImageInputStream(stream)
                    ?: throw IllegalArgumentException("Invalid image")
                imageInput.use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    if (!readers.hasNext()) throw IllegalArgumentException("Invalid image")
                    val reader = readers.next()
                    try {
                        reader.input = input
                        reader.read(0) // ensure image is decodable
                        reader.formatName.lowercase()
                    } finally { reader.dispose() }
                }
            }
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file", ex)
        }
        val ext = when (format) {
            "jpeg", "jpg" -> "jpg"
            "png" -> "png"
            "webp"-> "webp"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG and PNG supported")
        }
        val filename = "${UUID.randomUUID()}.$ext"
        val target = storage.resolve(filename)
        file.inputStream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        return UploadResponse(url = "/api/artworks/$filename")
    }

    @GetMapping("/{filename}")
    fun get(@PathVariable filename: String): ResponseEntity<Resource> {
        if (!Regex("^[a-f0-9-]{36}\\.(jpg|png|webp)$").matches(filename)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val path = storage.resolve(filename).normalize()
        if (!path.startsWith(storage) || !Files.isRegularFile(path)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val type = if (filename.endsWith(".png")) MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
            .body(FileSystemResource(path))
    }
}