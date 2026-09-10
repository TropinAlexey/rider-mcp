package com.github.tropin.ridermcp.testing

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.github.tropin.ridermcp.SessionManager
import com.github.tropin.ridermcp.mcpJson

@Serializable
data class RunTestsArgs(val configName: String? = null)

class RunTestsTool : AbstractMcpTool<RunTestsArgs>(RunTestsArgs.serializer()) {
    override val name = "rider_run_tests"
    override val description = "Runs tests. Omit configName to auto-detect; if multiple test configs exist, returns their names. Poll with rider_get_output."

    override fun handle(project: Project, args: RunTestsArgs): Response {
        val runManager = RunManager.getInstance(project)

        val testConfigs = runManager.allSettings.filter { config ->
            val id = config.type.id.lowercase()
            val displayName = config.type.displayName.lowercase()
            id.contains("test") || displayName.contains("test")
        }

        val settings = if (args.configName != null) {
            runManager.allSettings.find { it.name == args.configName }
                ?: return Response(error = "Configuration '${args.configName}' not found")
        } else when {
            testConfigs.isEmpty() -> return Response(error = "No test configurations found. Create one in Rider first.")
            testConfigs.size == 1 -> testConfigs.first()
            else -> {
                val result = buildJsonObject {
                    put("error", "Multiple test configs found, specify configName")
                    putJsonArray("configs") { testConfigs.forEach { add(it.name) } }
                }
                return Response(result.toString())
            }
        }

        val session = SessionManager.create("test")
        session.appendLine("Running: ${settings.name}")
        val configName = settings.name

        ApplicationManager.getApplication().invokeLater {
            val connection = project.messageBus.connect()
            connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
                override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    if (env.runProfile.name != configName) return
                    connection.disconnect()
                    handler.addProcessListener(object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                            val text = event.text.trimEnd('\n', '\r')
                            if (text.isNotEmpty()) session.appendLine(text)
                        }
                        override fun processTerminated(event: ProcessEvent) {
                            session.exitCode = event.exitCode
                            session.status = if (event.exitCode == 0) "passed" else "failed"
                        }
                    })
                }
            })

            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }

        return Response(mcpJson.encodeToString(mapOf("sessionId" to session.id)))
    }
}

class RerunFailedTestsTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name = "rider_rerun_failed_tests"
    override val description = "Reruns previously failed tests via the IDE's Rerun Failed Tests action."

    override fun handle(project: Project, args: NoArgs): Response {
        val action = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .getAction("RerunFailedTests")
            ?: return Response(error = "Rerun Failed Tests action not available")

        val session = SessionManager.create("test")
        session.appendLine("Rerunning failed tests")

        ApplicationManager.getApplication().invokeLater {
            val connection = project.messageBus.connect()
            connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
                override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    connection.disconnect()
                    handler.addProcessListener(object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                            val text = event.text.trimEnd('\n', '\r')
                            if (text.isNotEmpty()) session.appendLine(text)
                        }
                        override fun processTerminated(event: ProcessEvent) {
                            session.exitCode = event.exitCode
                            session.status = if (event.exitCode == 0) "passed" else "failed"
                        }
                    })
                }
            })

            val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                .build()
            val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(action, null, "", dataContext)
            action.actionPerformed(event)
        }

        return Response(mcpJson.encodeToString(mapOf("sessionId" to session.id)))
    }
}
