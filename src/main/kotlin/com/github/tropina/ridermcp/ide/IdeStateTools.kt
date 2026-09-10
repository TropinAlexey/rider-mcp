package com.github.tropina.ridermcp.ide

import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

private val json = Json { prettyPrint = false }

class GetIdeStateTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_ide_state"
    override val description = """
        Returns current IDE state: running progress indicators (indexing, building, analyzing),
        active editor file, and open tool windows.
        Use this to understand what Rider is currently doing.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val indicators = CoreProgressManager.getCurrentIndicators().map { indicator ->
            mapOf(
                "text" to (indicator.text ?: ""),
                "fraction" to if (indicator.isIndeterminate) -1.0 else indicator.fraction,
                "indeterminate" to indicator.isIndeterminate
            )
        }

        val activeFile = runReadAction {
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                val projectDir = project.guessProjectDir()?.toNioPathOrNull()
                val filePath = editor.virtualFile?.toNioPathOrNull()
                if (filePath != null && projectDir != null) {
                    projectDir.relativize(filePath).toString()
                } else {
                    editor.virtualFile?.path
                }
            }
        }

        val toolWindows = ToolWindowManager.getInstance(project).toolWindowIds.mapNotNull { id ->
            val tw = ToolWindowManager.getInstance(project).getToolWindow(id) ?: return@mapNotNull null
            mapOf(
                "id" to id,
                "isVisible" to tw.isVisible,
                "isActive" to tw.isActive
            )
        }

        val result = mapOf(
            "progressIndicators" to indicators,
            "activeFile" to (activeFile ?: "none"),
            "toolWindows" to toolWindows,
            "isBusy" to indicators.isNotEmpty()
        )
        return Response(json.encodeToString(result))
    }
}

class GetNotificationsTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_notifications"
    override val description = """
        Returns recent IDE notifications (balloon messages, event log entries).
        Useful for seeing background events: package restore results, indexing completion, plugin errors.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        // ponytail: NotificationsManager API is limited; get what we can from the balloon pool
        val notifications = NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(com.intellij.notification.Notification::class.java, project)
            .takeLast(50)
            .map { notification ->
                mapOf(
                    "title" to notification.title,
                    "content" to notification.content,
                    "type" to notification.type.name,
                    "group" to notification.groupId,
                    "timestamp" to notification.timestamp.toString()
                )
            }
        return Response(json.encodeToString(notifications))
    }
}

class ListToolWindowsTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_list_tool_windows"
    override val description = """
        Lists all available tool windows in Rider with their visibility state.
        Use this to discover which tool windows exist before calling rider_get_tool_window_content.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val twm = ToolWindowManager.getInstance(project)
        val windows = twm.toolWindowIds.mapNotNull { id ->
            val tw = twm.getToolWindow(id) ?: return@mapNotNull null
            mapOf(
                "id" to id,
                "isVisible" to tw.isVisible,
                "isActive" to tw.isActive,
                "isAvailable" to tw.isAvailable,
                "title" to (tw.stripeTitle ?: id)
            )
        }
        return Response(json.encodeToString(windows))
    }
}

@Serializable
data class ToolWindowArgs(val windowId: String)

class GetToolWindowContentTool : AbstractMcpTool<ToolWindowArgs>(ToolWindowArgs.serializer()) {
    override val name = "rider_get_tool_window_content"
    override val description = """
        Returns the text content of a specific tool window (Build, Problems, Event Log, Run, etc.).
        Use rider_list_tool_windows to discover available window IDs.
        Note: not all tool windows expose readable text content.
    """.trimIndent()

    override fun handle(project: Project, args: ToolWindowArgs): Response {
        val twm = ToolWindowManager.getInstance(project)
        val tw = twm.getToolWindow(args.windowId)
            ?: return Response(error = "Tool window '${args.windowId}' not found")

        // ponytail: tool window content extraction is non-trivial;
        // each window type has its own data model. Start with what we can get from the content manager.
        val contents = tw.contentManager.contents.map { content ->
            mapOf(
                "displayName" to (content.displayName ?: ""),
                "description" to (content.description ?: ""),
                "isSelected" to (content == tw.contentManager.selectedContent)
            )
        }

        return Response(json.encodeToString(mapOf(
            "windowId" to args.windowId,
            "isVisible" to tw.isVisible,
            "contents" to contents
        )))
    }
}
