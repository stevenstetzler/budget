package com.vidalabs.budget.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SafEventStore(
    private val context: Context,
    private val folderUri: Uri
) {
    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalStateException("Invalid sync folder URI")

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile {
        return parent.findFile(name) ?: parent.createDirectory(name)
        ?: throw IllegalStateException("Failed to create/find directory: $name")
    }

    private fun ensureFile(parent: DocumentFile, filename: String): DocumentFile {
        return parent.findFile(filename)
            ?: parent.createFile("application/json", filename)
            ?: throw IllegalStateException("Failed to create file: $filename")
    }

    /** events/<YYYY-MM>/... */
    fun listEventFiles(): List<DocumentFile> {
        val root = root()
        val eventsDir = ensureDir(root, "events")

        val out = mutableListOf<DocumentFile>()
        for (monthDir in (eventsDir.listFiles().filter { it.isDirectory }).sortedBy { it.name }) {
            val files = monthDir.listFiles()
                .filter { it.isFile && (it.name?.endsWith(".json") == true) }
                .sortedBy { it.name }
            out.addAll(files)
        }
        return out
    }

    fun readText(file: DocumentFile): String {
        val uri = file.uri
        return context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Failed to open input stream for $uri" }
            BufferedReader(InputStreamReader(input)).use { it.readText() }
        }
    }

    fun writeEventFile(monthDirName: String, filename: String, content: String) {
        val root = root()
        val eventsDir = ensureDir(root, "events")
        val monthDir = ensureDir(eventsDir, monthDirName)
        val f = ensureFile(monthDir, filename)

        context.contentResolver.openOutputStream(f.uri, "wt").use { out ->
            requireNotNull(out) { "Failed to open output stream for ${f.uri}" }
            OutputStreamWriter(out).use { it.write(content) }
        }
    }
}
