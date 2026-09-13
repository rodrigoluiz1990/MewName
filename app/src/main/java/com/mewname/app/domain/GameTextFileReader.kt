package com.mewname.app.domain

import java.io.BufferedReader

/** Reads each record once; avoids repeated whole-file regex matchers on Android. */
internal object GameTextFileReader {
    private val whitespace = Regex("\\s+")
    fun read(reader: BufferedReader): Map<String, String> = buildMap {
        var key: String? = null
        var readingText = false
        val value = StringBuilder()
        fun finish() {
            val id = key ?: return
            val text = value.toString().replace(whitespace, " ").trim()
            if (id.isNotBlank() && text.isNotBlank()) put(id, text)
        }
        reader.forEachLine { raw ->
            val line = raw.trimStart('\uFEFF', ' ', '\t')
            when {
                line.startsWith("RESOURCE ID:") -> {
                    finish()
                    key = line.substringAfter("RESOURCE ID:").trim()
                    value.setLength(0)
                    readingText = false
                }
                line.startsWith("TEXT:") && !readingText -> {
                    readingText = true
                    value.append(line.substringAfter("TEXT:")).append('\n')
                }
                readingText -> value.append(raw).append('\n')
            }
        }
        finish()
    }
}