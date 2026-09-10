package com.github.tropin.ridermcp.ide

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.github.tropin.ridermcp.projectDir
import com.github.tropin.ridermcp.relTo

class GetIdeStateTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_get_ide_state"
    override val description = "Returns IDE activity: progress indicators (indexing/building/analyzing), active file, whether IDE is busy."

    override fun handle(project: Project, args: NoArgs): Response {
        val indicators = CoreProgressManager.getCurrentIndicators()

        val activeFile = runReadAction {
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                val projectDir = project.projectDir()
                editor.virtualFile?.toNioPathOrNull()?.relTo(projectDir) ?: editor.virtualFile?.path
            }
        }

        val result = buildJsonObject {
            put("activeFile", activeFile ?: "none")
            put("isBusy", indicators.isNotEmpty())
            if (indicators.isNotEmpty()) {
                putJsonArray("progress") {
                    indicators.forEach { ind ->
                        addJsonObject {
                            ind.text?.takeIf { it.isNotEmpty() }?.let { put("text", it) }
                            if (!ind.isIndeterminate) put("fraction", ind.fraction)
                        }
                    }
                }
            }
        }
        return Response(result.toString())
    }
}

@Serializable
data class NotificationArgs(val limit: Int = 5)

class GetNotificationsTool : AbstractMcpTool<NotificationArgs>(NotificationArgs.serializer()) {
    override val name = "rider_get_notifications"
    override val description = "Returns recent IDE notifications. Default limit: 5, pass {\"limit\": N} for more."

    override fun handle(project: Project, args: NotificationArgs): Response {
        val notifications = NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)
            .takeLast(args.limit)

        val result = buildJsonArray {
            notifications.forEach { n ->
                addJsonObject {
                    n.title.takeIf { it.isNotEmpty() }?.let { put("title", it) }
                    n.content.takeIf { it.isNotEmpty() }?.let { put("content", it) }
                    put("type", n.type.name)
                    put("group", n.groupId)
                }
            }
        }
        return Response(result.toString())
    }
}

@Serializable
data class ListToolWindowsArgs(val all: Boolean = false)

class ListToolWindowsTool : AbstractMcpTool<ListToolWindowsArgs>(ListToolWindowsArgs.serializer()) {
    override val name = "rider_list_tool_windows"
    override val description = "Lists tool windows. By default only visible ones; pass {\"all\": true} for all."

    override fun handle(project: Project, args: ListToolWindowsArgs): Response {
        val twm = ToolWindowManager.getInstance(project)
        val result = buildJsonArray {
            twm.toolWindowIds.forEach { id ->
                val tw = twm.getToolWindow(id) ?: return@forEach
                if (!args.all && !tw.isVisible) return@forEach
                addJsonObject {
                    put("id", id)
                    if (args.all) put("visible", tw.isVisible)
                    if (tw.isActive) put("active", true)
                }
            }
        }
        return Response(result.toString())
    }
}

@Serializable
data class ToolWindowArgs(val windowId: String)

class GetToolWindowContentTool : AbstractMcpTool<ToolWindowArgs>(ToolWindowArgs.serializer()) {
    override val name = "rider_get_tool_window_content"
    override val description = "Returns tab names of a tool window. For build output use rider_get_build_output."

    override fun handle(project: Project, args: ToolWindowArgs): Response {
        val tw = ToolWindowManager.getInstance(project).getToolWindow(args.windowId)
            ?: return Response(error = "Tool window '${args.windowId}' not found")

        val tabs = tw.contentManager.contents.map { it.displayName ?: "" }
        val selected = tw.contentManager.selectedContent?.displayName

        val result = buildJsonObject {
            put("windowId", args.windowId)
            putJsonArray("tabs") { tabs.forEach { add(it) } }
            selected?.let { put("selected", it) }
        }
        return Response(result.toString())
    }
}
