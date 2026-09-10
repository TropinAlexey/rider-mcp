package com.github.tropin.ridermcp.context

import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.toNioPathOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.nio.file.Path

private val json = Json { prettyPrint = false }

private fun Project.projectDir(): Path? = guessProjectDir()?.toNioPathOrNull()

private fun Path.rel(projectDir: Path?): String =
    projectDir?.relativize(this)?.toString() ?: this.toString()

class GetOpenEditorsTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_open_editors"
    override val description = """
        Returns all open editor tabs with their file paths, ordered by most recently used.
        Shows which file is currently active.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val fem = FileEditorManager.getInstance(project)
        val projectDir = project.projectDir()
        val activeFile = fem.selectedTextEditor?.virtualFile

        val editors = fem.openFiles.map { file ->
            val path = file.toNioPathOrNull()?.rel(projectDir) ?: file.path
            mapOf(
                "path" to path,
                "isActive" to (file == activeFile),
                "isModified" to (com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                    .getDocument(file)?.let {
                        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().isDocumentUnsaved(it)
                    } ?: false)
            )
        }
        return Response(json.encodeToString(editors))
    }
}

class GetCursorContextTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_cursor_context"
    override val description = """
        Returns the current cursor position: file path, line number, column, and surrounding lines of code.
        Useful for understanding what the programmer is looking at right now.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response = runReadAction {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return@runReadAction Response(error = "No active editor")

        val projectDir = project.projectDir()
        val filePath = editor.virtualFile?.toNioPathOrNull()?.rel(projectDir) ?: "unknown"
        val caret = editor.caretModel.primaryCaret
        val line = caret.logicalPosition.line
        val column = caret.logicalPosition.column
        val document = editor.document
        val totalLines = document.lineCount

        val contextStart = maxOf(0, line - 5)
        val contextEnd = minOf(totalLines - 1, line + 5)
        val surroundingLines = (contextStart..contextEnd).map { lineNum ->
            val start = document.getLineStartOffset(lineNum)
            val end = document.getLineEndOffset(lineNum)
            val prefix = if (lineNum == line) ">>> " else "    "
            "$prefix${lineNum + 1}: ${document.getText(com.intellij.openapi.util.TextRange(start, end))}"
        }

        val result = mapOf(
            "file" to filePath,
            "line" to (line + 1),
            "column" to (column + 1),
            "totalLines" to totalLines,
            "context" to surroundingLines.joinToString("\n")
        )
        Response(json.encodeToString(result))
    }
}

class GetSelectionTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_selection"
    override val description = """
        Returns the currently selected text in the active editor.
        Returns empty if nothing is selected.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response = runReadAction {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return@runReadAction Response(error = "No active editor")

        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrEmpty()) {
            return@runReadAction Response(json.encodeToString(mapOf("selected" to false)))
        }

        val projectDir = project.projectDir()
        val filePath = editor.virtualFile?.toNioPathOrNull()?.rel(projectDir) ?: "unknown"
        val selStart = editor.selectionModel.selectionStart
        val selEnd = editor.selectionModel.selectionEnd
        val startLine = editor.document.getLineNumber(selStart) + 1
        val endLine = editor.document.getLineNumber(selEnd) + 1

        Response(json.encodeToString(mapOf(
            "selected" to true,
            "file" to filePath,
            "startLine" to startLine,
            "endLine" to endLine,
            "text" to selectedText
        )))
    }
}

class GetRecentFilesTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_recent_files"
    override val description = """
        Returns the 20 most recently opened files, ordered by access time (newest first).
        Shows what the programmer has been working on.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val projectDir = project.projectDir()
        val recentFiles = EditorHistoryManager.getInstance(project).fileList.takeLast(20).reversed()
        val files = recentFiles.map { file ->
            file.toNioPathOrNull()?.rel(projectDir) ?: file.path
        }
        return Response(json.encodeToString(files))
    }
}

class GetBookmarksTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_bookmarks"
    override val description = """
        Returns all bookmarks in the project with their file paths and line numbers.
    """.trimIndent()

    @Suppress("DEPRECATION")
    override fun handle(project: Project, args: NoArgs): Response {
        // ponytail: BookmarkManager API varies across IDE versions; using the stable one
        val projectDir = project.projectDir()
        try {
            val bookmarkManager = BookmarkManager.getInstance(project)
            val bookmarks = bookmarkManager.validBookmarks.map { bookmark ->
                mapOf(
                    "file" to (bookmark.file?.toNioPathOrNull()?.rel(projectDir) ?: "unknown"),
                    "line" to (bookmark.line + 1),
                    "description" to (bookmark.description ?: "")
                )
            }
            return Response(json.encodeToString(bookmarks))
        } catch (e: Exception) {
            return Response(error = "Bookmarks API not available: ${e.message}")
        }
    }
}
