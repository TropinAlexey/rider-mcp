package com.github.tropin.ridermcp.process

import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.github.tropin.ridermcp.mcpJson

class ListProcessesTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_list_processes"
    override val description = "Lists running processes managed by Rider with PID and command line."

    override fun handle(project: Project, args: NoArgs): Response {
        val processes = ExecutionManager.getInstance(project).getRunningDescriptors { true }.mapNotNull { d ->
            val handler = d.processHandler ?: return@mapNotNull null
            if (handler.isProcessTerminated || handler.isProcessTerminating) return@mapNotNull null
            buildJsonObject {
                put("name", d.displayName ?: "unknown")
                if (handler is OSProcessHandler) {
                    try { put("pid", handler.process.pid()) } catch (_: Exception) {}
                    handler.commandLine?.let { put("commandLine", it) }
                }
            }
        }
        return Response(mcpJson.encodeToString(JsonArray(processes)))
    }
}

@Serializable
data class KillProcessArgs(val processName: String)

class KillProcessTool : AbstractMcpTool<KillProcessArgs>(KillProcessArgs.serializer()) {
    override val name = "rider_kill_process"
    override val description = "Kills a running process by display name (from rider_list_processes)."

    override fun handle(project: Project, args: KillProcessArgs): Response {
        val descriptors = ExecutionManager.getInstance(project).getRunningDescriptors { true }
        val target = descriptors.find { it.displayName == args.processName }
            ?: return Response(error = "Process '${args.processName}' not found")

        val handler = target.processHandler
            ?: return Response(error = "No process handler for '${args.processName}'")

        if (handler.isProcessTerminated) return Response(error = "Process already terminated")

        handler.destroyProcess()
        return Response("ok")
    }
}
