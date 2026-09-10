package com.github.tropin.ridermcp.build

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.github.tropin.ridermcp.SessionManager
import com.github.tropin.ridermcp.mcpJson

class StartBuildTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_start_build"
    override val description = """
        Starts a solution build in Rider and returns a session ID.
        Use rider_get_build_output with the returned sessionId to poll build status.
        Returns: {"sessionId": "build_1"}
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val session = SessionManager.create("build")
        session.appendLine("Build started")

        ProjectTaskManager.getInstance(project).buildAllModules()
            .onSuccess { result ->
                session.status = when {
                    result.isAborted -> "cancelled"
                    result.hasErrors() -> "failed"
                    else -> "succeeded"
                }
                session.exitCode = if (result.hasErrors()) 1 else 0
                session.appendLine("Build ${session.status}")
            }
            .onError { error ->
                session.status = "failed"
                session.exitCode = 1
                session.appendLine("Build error: ${error.message}")
            }

        return Response(mcpJson.encodeToString(mapOf("sessionId" to session.id)))
    }
}

@Serializable
data class SessionIdArgs(val sessionId: String)

@Serializable
data class BuildOutputResult(
    val status: String,
    val newLines: List<String>,
    val progress: Double,
    val exitCode: Int? = null
)

class GetBuildOutputTool : AbstractMcpTool<SessionIdArgs>(SessionIdArgs.serializer()) {
    override val name = "rider_get_build_output"
    override val description = """
        Returns new build output lines since last call for the given session.
        Poll this repeatedly until status is not "running".
        Returns: {"status": "running|succeeded|failed|cancelled", "newLines": [...], "progress": 0.0-1.0, "exitCode": 0|1|null}
    """.trimIndent()

    override fun handle(project: Project, args: SessionIdArgs): Response {
        val session = SessionManager.get(args.sessionId)
            ?: return Response(error = "Session '${args.sessionId}' not found")

        val result = BuildOutputResult(
            status = session.status,
            newLines = session.getNewLines(),
            progress = session.progress,
            exitCode = session.exitCode
        )
        return Response(mcpJson.encodeToString(result))
    }
}

class CancelBuildTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_cancel_build"
    override val description = """
        Cancels the currently running build in Rider.
        Returns "ok" if cancellation was requested.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val action = ActionManager.getInstance().getAction("Stop")
            ?: return Response(error = "No cancel action available")

        ApplicationManager.getApplication().invokeLater {
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build()
            val event = AnActionEvent.createFromAnAction(action, null, "", dataContext)
            action.actionPerformed(event)
        }
        return Response("ok")
    }
}
