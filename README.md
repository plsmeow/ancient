# Ancient Client NextGen

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-4b8b3b?style=for-the-badge&logo=minecraft&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)

## О проекте

NextGen версия Ancient Client.

## Требования

- Minecraft **26.3**
- Fabric Loader **0.19.5 или новее**
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.164.0+26.3**
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) **1.14.2+kotlin.2.4.21**
- Java **25**

## Зависимости

### Обязательные
- [Fabric Loader](https://fabricmc.net/use/) - загрузчик модов
- [Fabric API](https://modrinth.com/mod/fabric-api) - API для работы с Fabric
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) - поддержка Kotlin

## Сборка

### Требования для сборки
- Java **25**

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

1. Установите Java 25 для запуска игры.
2. Установите [Fabric Loader](https://fabricmc.net/use/) для Minecraft 26.3.
3. Скачайте и установите зависимости в папку `mods`:
   - [Fabric API 0.164.0+26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3&l=fabric)
   - [Fabric Language Kotlin 1.14.2](https://modrinth.com/mod/fabric-language-kotlin/versions?g=26.3)
4. Скопируйте собранный `.jar` из `build/libs/` или скачайте [готовый релиз](https://github.com/plsmeow/ancient/releases) в папку `mods`.
5. Запустите Minecraft 26.3 с Fabric.

## Релизы

Автоматические сборки доступны в разделе [Releases](https://github.com/plsmeow/ancient/releases).

## Legacy версия

Версия для Minecraft 1.21.4 доступна в ветке [`legacy-1.21.4`](https://github.com/plsmeow/ancient/tree/legacy-1.21.4).
