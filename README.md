# Ancient Client

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-4b8b3b?style=for-the-badge&logo=minecraft&logoColor=white)

## О проекте

Чит был основан на базе [moonward](https://yougame.biz/threads/376596/)

## Требования

- Minecraft **1.21.4**
- Fabric Loader **0.17.3 или новее**
- Fabric API для Minecraft 1.21.4
- Java **21**

## Сборка

Клонируйте репозиторий и запустите Gradle Wrapper из корня проекта:

### Windows

```powershell
.\gradlew.bat build
```

### Linux / macOS

```bash
./gradlew build
```

Готовые jar появятся в `build/libs/`.

## Установка

1. Установите Java 21.
2. Установите [Fabric Loader](https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar) для Minecraft 1.21.4.
3. Установите [Fabric API](https://cdn.modrinth.com/data/P7dR8mSH/versions/p96k10UR/fabric-api-0.119.4%2B1.21.4.jar?mr_download_reason=standalone&mr_game_version=1.21.4&mr_loader=fabric).
4. Скопируйте скачаный или собранный из `build/libs/` `.jar` в папку `mods`.
5. Запустите Minecraft 1.21.4 с Fabric.