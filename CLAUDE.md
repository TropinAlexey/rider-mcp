# Rider MCP Extension Plugin

IntelliJ/Rider плагин, расширяющий JetBrains MCP Server дополнительными инструментами для полного управления IDE.

## Архитектура

- **Тип**: IntelliJ Platform Plugin (Kotlin)
- **Зависимость**: `com.intellij.mcpServer` — основной JetBrains MCP Server plugin
- **Extension point**: `com.intellij.mcpServer.mcpTool` — регистрация новых MCP tools
- **Target IDE**: JetBrains Rider 2024.3+
- **JDK**: 21

## Паттерн для новых tools

Каждый tool:
1. Наследует `AbstractMcpTool<Args>`
2. `Args` — `@Serializable` data class (или `NoArgs`)
3. Регистрируется в `src/main/resources/META-INF/plugin.xml`
4. Возвращает `Response(jsonString)` или `Response(error = "...")`

Для долгих операций (build, test run) — **polling pattern**:
- `start_*` → создаёт `OutputSession` через `SessionManager`, возвращает `sessionId`
- `get_*_output(sessionId)` → возвращает новые строки с момента последнего вызова
- Клиент поллит пока `status != "running"`

## Структура

```
src/main/kotlin/com/github/tropin/ridermcp/
├── SessionManager.kt       # Общая инфраструктура polling sessions
├── build/                   # P0: Build observability
├── process/                 # P0: Process management
├── ide/                     # P0: IDE state, tool windows, notifications
├── context/                 # P1: Programmer context (editors, cursor, selection)
├── testing/                 # P1: Test runner (TODO)
├── debugger/                # P2: .NET debugger (TODO)
├── nuget/                   # P2: NuGet management (TODO)
└── settings/                # P3: IDE settings management (TODO)
```

## Команды

```bash
./gradlew buildPlugin          # Собрать plugin zip
./gradlew runIde               # Запустить Rider с плагином для отладки
./gradlew verifyPlugin         # Проверить совместимость
```

## Naming convention

Все tool names начинаются с `rider_` чтобы не конфликтовать с основным MCP Server plugin.

## Язык

Код и комментарии — на английском. Документация проекта (CLAUDE.md, TODO.md) — на русском.
Общение с разработчиком — на русском.
