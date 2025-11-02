package com.sanarei.sanareimobileapp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream

/**
 * SanareiDepacketizer
 * --------------------
 *
 * Reconstructs the original text that was packetized by the Ruby Packetizer.
 *
 * IMPORTANT: The first step expected here is that each incoming message string
 * is Base64-decoded to obtain the underlying JSON packet string. This mirrors
 * how the USSD transport encodes packets for transit.
 *
 * Packet JSON schema (after decoding the outer Base64 message):
 * {
 *   "id": Int,                 // 1-based position
 *   "prev": Int | null,        // optional, not used here
 *   "next": Int | null,        // optional, not used here
 *   "checksum": String,        // lowercase hex CRC32 of payload bytes
 *   "payload": String          // Base64 of gzip-compressed bytes
 * }
 *
 * Steps:
 * 1) Base64-decode each message String to get a JSON object string.
 * 2) Parse minimal fields from JSON (id, checksum, payload).
 * 3) Sort packets by id and verify the sequence is contiguous starting at 1.
 * 4) Base64-decode each payload to raw bytes, optionally verify CRC32.
 * 5) Concatenate payload bytes in order and GZIP-decompress to the original text.
 */
object SanareiDepacketizer {
    /**
     * Depacketize a list of base64-encoded JSON messages into the original text.
     *
     * @param messages List of message strings. Each element must be a Base64 string wrapping a JSON packet.
     * @param verifyChecksum Whether to verify CRC32 checksums for each payload chunk (default true).
     * @param charset Charset used to decode the final text (default UTF-8).
     * @throws IllegalArgumentException if input is empty, malformed, or sequence is broken.
     * @throws RuntimeException if checksum verification fails or gzip inflate fails.
     */
    @JvmStatic
    fun depacketize(
        messages: List<String>,
        verifyChecksum: Boolean = true,
        charset: Charset = Charsets.UTF_8
    ): String {
        require(messages.isNotEmpty()) { "no packets provided" }

        // 1) Outer Base64 decode to get JSON strings
        val jsonPackets = messages.mapIndexed { idx, msg ->
            try {
                val decoded = Base64.getDecoder().decode(msg.trim())
                decoded.toString(Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("packet at index $idx is not valid Base64: ${e.message}")
            }
        }

        // 2) Parse minimal fields from JSON
        val packets = jsonPackets.mapIndexed { idx, json ->
            val map = parseFlatJson(json)
            val id = map["id"]?.toIntOrNull()
                ?: throw IllegalArgumentException("packet at index $idx missing/invalid id")
            val checksum = map["checksum"] ?: throw IllegalArgumentException("packet #$id missing checksum")
            val payloadB64 = map["payload"] ?: throw IllegalArgumentException("packet #$id missing payload")
            Packet(id = id, checksum = checksum.lowercase(), payloadBase64 = payloadB64)
        }

        // 3) Sort by id and check contiguity
        val sorted = packets.sortedBy { it.id }
        sorted.forEachIndexed { i, p ->
            val expected = i + 1
            if (p.id != expected) {
                throw IllegalArgumentException("packet id sequence broken: expected $expected, got ${p.id}")
            }
        }

        // 4) Decode payloads and optional checksum verification
        val allBytes = ByteArrayOutputStream()
        sorted.forEachIndexed { idx, p ->
            val bytes = try {
                Base64.getDecoder().decode(p.payloadBase64)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("packet #${p.id} payload is not valid Base64: ${e.message}")
            }

            if (verifyChecksum) {
                val crc = CRC32().apply { update(bytes) }.value
                val calc = crc.toString(16).padStart(8, '0')
                if (!calc.equals(p.checksum, ignoreCase = true)) {
                    throw RuntimeException("checksum mismatch on packet #${p.id} (index $idx): expected ${p.checksum}, got $calc")
                }
            }

            allBytes.write(bytes)
        }

        // 5) GZIP-decompress
        val gzBytes = allBytes.toByteArray()
        val inflated = gunzip(gzBytes)
        return inflated.toString(charset)
    }

    // Minimal flat JSON parser for our limited schema without external dependencies.
    // Expects a single JSON object with string or number values; ignores unknown keys.
    private fun parseFlatJson(json: String): Map<String, String> {
        // Very small and lenient tokenizer for the known schema.
        // We avoid a full JSON library to keep this file standalone.
        val trimmed = json.trim()
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            throw IllegalArgumentException("invalid packet (not a JSON object)")
        }
        val body = trimmed.substring(1, trimmed.length - 1)
        val result = mutableMapOf<String, String>()

        // Split on commas that are not inside quotes.
        var i = 0
        var start = 0
        var inString = false
        val parts = mutableListOf<String>()
        while (i < body.length) {
            val c = body[i]
            if (c == '"') {
                // toggle unless escaped
                val escaped = i > 0 && body[i - 1] == '\\'
                if (!escaped) inString = !inString
            } else if (c == ',' && !inString) {
                parts.add(body.substring(start, i))
                start = i + 1
            }
            i++
        }
        if (start <= body.length) {
            val tail = body.substring(start)
            if (tail.isNotBlank()) parts.add(tail)
        }

        parts.forEach { pair ->
            val idx = pair.indexOf(':')
            if (idx <= 0) return@forEach
            val keyRaw = pair.substring(0, idx).trim()
            val valRaw = pair.substring(idx + 1).trim()
            val key = stripQuotes(keyRaw)
            val value = when {
                valRaw.equals("null", ignoreCase = true) -> ""
                valRaw.startsWith("\"") -> stripQuotes(valRaw)
                else -> valRaw.trim()
            }
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }

    private fun stripQuotes(s: String): String {
        var t = s.trim()
        if (t.startsWith('"') && t.endsWith('"') && t.length >= 2) {
            t = t.substring(1, t.length - 1)
        }
        // unescape basic JSON escapes for quotes and backslash
        return t.replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun gunzip(bytes: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gis ->
            val buf = ByteArray(8 * 1024)
            val out = ByteArrayOutputStream()
            while (true) {
                val n = gis.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private data class Packet(
        val id: Int,
        val checksum: String,
        val payloadBase64: String
    )
}
