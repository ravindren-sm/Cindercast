package io.github.ravindren_sm.openstream.data.tmdb

import io.github.ravindren_sm.openstream.provider.AesGcmBox
import io.github.ravindren_sm.openstream.provider.SealedBoxException
import java.io.File
import java.io.IOException

/** Keeps the viewer's own tag-service key sealed on the device (the same sealing as the portal details). Never logged, never in a backup in clear. */
class TmdbKeyStore(private val file: File, private val box: AesGcmBox) {
    fun load(): String? {
        if (!file.isFile) return null
        return try { String(box.open(file.readBytes(), AAD), Charsets.UTF_8).takeIf { it.isNotBlank() } } catch (_: SealedBoxException) { null } catch (_: IOException) { null }
    }

    @Throws(IOException::class)
    fun save(key: String) {
        val sealed = box.seal(key.trim().toByteArray(Charsets.UTF_8), AAD)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("could not store the key") }
    }

    fun clear() { file.delete() }

    private companion object { val AAD = "openstream-tag-key-v1".toByteArray() }
}
