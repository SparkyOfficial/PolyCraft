<div align="center">
  <h1>🚀 PolyCraft Engine</h1>
  <p>
    <em>⚠️ New Release: This plugin is in its early stages and may contain bugs. Please report any issues you encounter. ⚠️</em>
    <br>
    <em>⚠️ Новая версия: Плагин находится на ранней стадии разработки и может содержать ошибки. Пожалуйста, сообщайте о любых проблемах. ⚠️</em>
  </p>

  [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
  [![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4-blue)](https://papermc.io/)
  [![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

  <p>
    <em>Inspired by the original ScriptCraft project, but rebuilt for modern Minecraft versions with enhanced features and better performance.</em>
    <br>
    <em>Вдохновлено оригинальным проектом ScriptCraft, но переработано для современных версий Minecraft с улучшенными функциями и производительностью.</em>
  </p>
</div>

## 🌟 About / О проекте

**English**:
PolyCraft Engine is a high-performance scripting platform that transforms your Minecraft server into a powerful development environment. Create plugins in JavaScript without server restarts. Built on GraalVM for maximum performance and compatibility.

**Русский**:
PolyCraft Engine — это высокопроизводительная платформа для выполнения скриптов, которая превращает ваш Minecraft-сервер в мощную среду разработки. Создавайте плагины на JavaScript без необходимости перезапуска сервера. Построена на GraalVM для максимальной производительности и совместимости.

## 🚀 Features / Возможности

- **Language Support / Поддержка языков**: JavaScript
- **Hot Reload / Горячая перезагрузка**: Changes apply instantly / Изменения применяются мгновенно
- **Enhanced Security / Безопасность**: Isolated execution environment / Изолированная среда выполнения
- **High Performance / Высокая производительность**: Optimized for modern hardware / Оптимизирована для современного железа
- **Modern API / Современный API**: Full access to server features / Полный доступ к возможностям сервера
- **Debugging Tools / Инструменты отладки**: Detailed error reporting / Подробные сообщения об ошибках

## 📦 Requirements / Требования

- Java 17 or higher / Java 17 или выше
- Paper/Spigot 1.20.4+ (Tested on 1.20.4, may work on other versions) / (Протестировано на 1.20.4, может работать на других версиях)
- 2GB+ RAM (4GB+ recommended for complex scripts) / 2 ГБ+ ОЗУ (4+ ГБ рекомендуется для сложных скриптов)

## 🚀 Installation / Установка

1. Download the latest `PolyCraft-Engine.jar` from [Releases](https://github.com/yourusername/polycraft-engine/releases)
2. Place the file in your server's `plugins/` folder
3. Restart the server
4. The plugin will create necessary configuration files and folders

1. Скачайте последнюю версию `PolyCraft-Engine.jar` из раздела [Releases](https://github.com/yourusername/polycraft-engine/releases)
2. Поместите файл в папку `plugins/` вашего сервера
3. Перезапустите сервер
4. Плагин создаст необходимые файлы конфигурации и папки

## 🛠️ Getting Started / Начало работы

### Directory Structure / Структура каталогов

```
plugins/
└── PolyCraft/
    ├── config.yml       # Main config / Основной конфиг
    ├── scripts/         # Scripts (.js) / Скрипты
    ├── lib/             # External libraries / Внешние библиотеки
    └── data/            # Script data / Данные скриптов
```

### Basic Commands / Основные команды

| Command / Команда | Description / Описание |
|-------------------|------------------------|
| `/pc list` | List all scripts / Список всех скриптов |
| `/pc reload <script>` | Reload a script / Перезагрузить скрипт |
| `/pc enable <script>` | Enable a script / Включить скрипт |
| `/pc disable <script>` | Disable a script / Выключить скрипт |
| `/pc eval <code>` | Execute JavaScript code directly / Выполнить JavaScript код напрямую |
| `/pc help` | Show help / Показать справку |

## 📚 Documentation / Документация

### JavaScript Example / Пример на JavaScript

```javascript
// Called when script loads
function onEnable() {
    poly.log("Script loaded!");
    
    // Register command
    poly.registerCommand("hello", (sender, args) => {
        sender.sendMessage(`Hello, ${sender.getName() || 'console'}!`);
        return true;
    });
    
    // Async operation
    poly.runAsync(() => {
        // Code runs in separate thread
    });
}

// Called when script unloads
function onDisable() {
    poly.log("Script unloaded!");
}

// Event handling
poly.on("player.PlayerJoinEvent", (event) => {
    const player = event.getPlayer();
    player.sendMessage("Welcome to the server!");
});
```

## 🔒 Security / Безопасность

- **Isolation / Изоляция**: Each script runs in separate context / Каждый скрипт выполняется в отдельном контексте
- **Access Control / Контроль доступа**: Limited server API access / Ограниченный доступ к API сервера
- **Resource Quotas / Квоты ресурсов**: CPU and memory limits / Ограничения на использование ЦП и памяти
- **Whitelisting / Белые списки**: Only trusted operations allowed / Разрешены только доверенные операции


## 📜 License / Лицензия

This project is licensed under the GPL-3.0 License - see the [LICENSE](LICENSE) file for details.

Этот проект распространяется под лицензией GPL-3.0 - подробности в файле [LICENSE](LICENSE).

## 🔗 Inspired by ScriptCraft / Вдохновлено ScriptCraft

PolyCraft Engine is a spiritual successor to the original [ScriptCraft](https://github.com/walterhiggins/ScriptCraft) project. While ScriptCraft was groundbreaking in its time, it became outdated and unsupported. We've taken the core concepts and rebuilt them for modern Minecraft versions with better performance, security, and features.

PolyCraft Engine является духовным преемником оригинального проекта [ScriptCraft](https://github.com/walterhiggins/ScriptCraft). Хотя ScriptCraft был новаторским в своё время, он устарел и больше не поддерживается. Мы взяли основные концепции и переработали их для современных версий Minecraft с улучшенной производительностью, безопасностью и функционалом.
