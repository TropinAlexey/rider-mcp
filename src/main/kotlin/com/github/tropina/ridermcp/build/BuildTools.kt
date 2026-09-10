package com.github.tropina.ridermcp.build

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.github.tropina.ridermcp.SessionManager

private val json = Json { prettyPrint = false }

class StartBuildTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_start_build"
    override val description = """
        Starts a solution build in Rider and returns a session ID for polling output.
        Use rider_get_build_output with the returned sessionId to get streaming build output.
        Returns: {"sessionId": "build_1"}
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val session = SessionManager.create("build")

        ApplicationManager.getApplication().invokeLater {
            val compiler = CompilerManager.getInstance(project)
            compiler.make(project.modules()) { aborted, errors, warnings, context ->
                if (aborted) {
                    session.status = "cancelled"
                } else if (errors > 0) {
                    session.status = "failed"
                    session.exitCode = 1
                } else {
                    session.status = "succeeded"
                    session.exitCode = 0
                }
                session.appendLine("Build finished: ${if (aborted) "cancelled" else if (errors > 0) "$errors error(s)" else "success"}, $warnings warning(s)")
            }
        }

        return Response(json.encodeToString(mapOf("sessionId" to session.id)))
    }

    private fun Project.modules() =
        com.intellij.openapi.module.ModuleManager.getInstance(this).modules
}

@Serializable
data class SessionIdArgs(val sessionId: String)

class GetBuildOutputTool : AbstractMcpTool<SessionIdArgs>(SessionIdArgs.serializer()) {
    override val name = "rider_get_build_output"
    override val description = """
        Returns new build output lines since last call for the given session.
        Poll this repeatedly until status is not "running".
        Returns: {"status": "running|succeeded|failed|cancelled", "newLines": [...], "progress": 0.0-1.0}
    """.trimIndent()

    override fun handle(project: Project, args: SessionIdArgs): Response {
        val session = SessionManager.get(args.sessionId)
            ?: return Response(error = "Session '${args.sessionId}' not found")

        val result = mapOf(
            "status" to session.status,
            "newLines" to session.getNewLines(),
            "progress" to session.progress,
            "exitCode" to (session.exitCode?.toString() ?: "null")
        )
        return Response(json.encodeToString(result))
    }
}

class CancelBuildTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_cancel_build"
    override val description = """
        Cancels the currently running build in Rider.
        Returns "ok" if cancellation was requested.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        // ponytail: CompilerManager doesn't expose cancel directly; use the action
        val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        val action = actionManager.getAction("CompileDirty.Cancel")
            ?: actionManager.getAction("Stop")

        if (action != null) {
            ApplicationManager.getApplication().invokeLater {
                val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                    action, null, "",
                    com.intellij.ide.DataManager.getInstance().getDataContext()
                )
                action.actionPerformed(event)
            }
            return Response("ok")
        }
        return Response(error = "No cancel action available")
    }
}
