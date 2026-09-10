package com.github.tropin.ridermcp.build

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.github.tropin.ridermcp.SessionManager

private val json = Json { prettyPrint = false }

class StartBuildTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_start_build"
    override val description = """
        Starts a solution build in Rider via the Build Solution action and returns a session ID.
        Use rider_get_build_output with the returned sessionId to poll build output.
        Returns: {"sessionId": "build_1"}
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val session = SessionManager.create("build")

        // ponytail: trigger build via IDE action — works across all JetBrains IDEs
        ApplicationManager.getApplication().invokeLater {
            val actionManager = ActionManager.getInstance()
            val buildAction = actionManager.getAction("CompileDirty")
                ?: actionManager.getAction("BuildSolutionAction")

            if (buildAction != null) {
                val event = AnActionEvent.createFromAnAction(
                    buildAction, null, "",
                    DataManager.getInstance().getDataContext()
                )
                buildAction.actionPerformed(event)
                session.appendLine("Build started")
            } else {
                session.status = "failed"
                session.appendLine("No build action found")
            }
        }

        return Response(json.encodeToString(mapOf("sessionId" to session.id)))
    }
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
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("Stop")

        if (action != null) {
            ApplicationManager.getApplication().invokeLater {
                val event = AnActionEvent.createFromAnAction(
                    action, null, "",
                    DataManager.getInstance().getDataContext()
                )
                action.actionPerformed(event)
            }
            return Response("ok")
        }
        return Response(error = "No cancel action available")
    }
}
