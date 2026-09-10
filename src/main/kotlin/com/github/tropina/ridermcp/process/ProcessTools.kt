package com.github.tropina.ridermcp.process

import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

private val json = Json { prettyPrint = false }

class ListProcessesTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_list_processes"
    override val description = """
        Lists all processes managed by Rider (build, test runners, dev servers, run configurations).
        Returns JSON array of process info: name, pid (if available), status, command line snippet.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val descriptors = ExecutionManager.getInstance(project).getRunningDescriptors { true }
        val processes = descriptors.mapNotNull { descriptor ->
            val handler = descriptor.processHandler ?: return@mapNotNull null
            val name = descriptor.displayName ?: "unknown"
            val isRunning = !handler.isProcessTerminated && !handler.isProcessTerminating
            mapOf(
                "name" to name,
                "status" to if (isRunning) "running" else "terminated",
                "canKill" to isRunning.toString()
            )
        }
        return Response(json.encodeToString(processes))
    }
}

@Serializable
data class KillProcessArgs(val processName: String)

class KillProcessTool : AbstractMcpTool<KillProcessArgs>(KillProcessArgs.serializer()) {
    override val name = "rider_kill_process"
    override val description = """
        Kills a running process by its display name (from rider_list_processes).
        Returns "ok" if the process was found and kill was requested.
    """.trimIndent()

    override fun handle(project: Project, args: KillProcessArgs): Response {
        val descriptors = ExecutionManager.getInstance(project).getRunningDescriptors { true }
        val target = descriptors.find { it.displayName == args.processName }
            ?: return Response(error = "Process '${args.processName}' not found")

        val handler = target.processHandler
            ?: return Response(error = "No process handler for '${args.processName}'")

        if (handler.isProcessTerminated) {
            return Response(error = "Process already terminated")
        }

        handler.destroyProcess()
        return Response("ok")
    }
}
