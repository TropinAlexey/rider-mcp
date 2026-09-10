# Rider MCP Extension — Roadmap

## P0 — Критически нужно

### Build & Publish наблюдаемость
- [x] `rider_start_build` — запуск сборки с session ID
- [x] `rider_get_build_output` — polling вывода сборки
- [x] `rider_cancel_build` — отмена текущей сборки
- [ ] Подключить CompilerMessageHandler для реального потокового вывода MSBuild
- [ ] `rider_start_publish` — запуск publish profile с polling
- [ ] `rider_get_publish_output` — polling вывода publish
- [ ] Настройка MSBuild verbosity через tool

### Process management
- [x] `rider_list_processes` — список процессов Rider
- [x] `rider_kill_process` — убить процесс по имени
- [ ] Показывать PID и command line процессов
- [ ] Фильтрация по типу (MSBuild, compiler, test runner, dev server)

### IDE State
- [x] `rider_get_ide_state` — прогресс-индикаторы, активный файл, tool windows
- [x] `rider_get_notifications` — balloon уведомления и event log
- [x] `rider_list_tool_windows` — список tool windows с состоянием
- [x] `rider_get_tool_window_content` — содержимое tool window (базовое)
- [ ] Глубокая экстракция контента для Build Output, Problems, Event Log
- [ ] Подписка на новые уведомления (через polling session)

## P1 — Основной рабочий flow

### Programmer Context
- [x] `rider_get_context` — объединённый: файл + курсор + код + выделение + вкладки + закладки
- [x] `rider_get_recent_files` — последние открытые файлы

### Test Runner
- [x] `rider_run_tests` — запуск тестов (auto-detect config или по имени)
- [x] `rider_get_output` — единый polling tool для build/test/любых сессий
- [x] `rider_rerun_failed_tests` — перезапуск упавших через IDE action
- [ ] Дерево результатов с stack traces (извлечение из SMTestProxy)
- [ ] Фильтрация: запуск тестов по file/class/method

### Run/Debug Configuration Management
- [ ] `rider_create_run_config` — создание новой конфигурации
- [ ] `rider_update_run_config` — изменение параметров (env, args, pre-build)
- [ ] `rider_delete_run_config` — удаление

## P2 — Расширенные возможности

### .NET Debugger
- [ ] `rider_set_breakpoint` / `rider_remove_breakpoint`
- [ ] `rider_start_debug` — запуск debugging session
- [ ] `rider_debug_evaluate` — evaluate expression
- [ ] `rider_debug_stacktrace` — stack trace + locals
- [ ] `rider_debug_step` — step over / step into / continue

### NuGet
- [ ] `rider_list_packages` — установленные пакеты с версиями
- [ ] `rider_add_package` / `rider_update_package` / `rider_remove_package`
- [ ] `rider_nuget_restore` — restore с выводом

### IDE Settings
- [ ] `rider_get_setting` / `rider_set_setting` — чтение/запись настроек
- [ ] `rider_list_settings` — поиск настроек по ключевому слову
- [ ] `rider_manage_inspection` — вкл/выкл инспекций, severity

## P3 — Nice to have

### Terminal Streaming
- [ ] Polling session для вывода команд в Rider terminal
- [ ] Список открытых терминалов
- [ ] Отправка input в работающий терминал

### Tool Windows — глубокая интеграция
- [ ] Database: результаты SQL, структура
- [ ] TODO: все TODO/FIXME/HACK
- [ ] Endpoints: API маршруты
- [ ] Services: Docker, dev servers
- [ ] Git Log: визуальная история

### Plugin/Action Management
- [ ] `rider_manage_plugin` — install/enable/disable
- [ ] `rider_manage_file_watcher` — CRUD file watchers
- [ ] `rider_invalidate_caches` — Invalidate Caches & Restart

## Технические задачи

- [ ] Настроить CI (GitHub Actions) для сборки плагина
- [ ] Опубликовать в JetBrains Marketplace (после стабилизации)
- [ ] Протестировать совместимость с Rider 2025.1
- [x] Написать README.md с инструкцией по установке
- [x] Проверить что `build.gradle.kts` собирает с Rider (RD) а не IntelliJ Community (IC)
- [x] Код-ревью + оптимизация токенов (15→12 tools, compact JSON)
