# Telegram-бот "Мастер коктейлей"

Этот проект представляет собой телеграм-бота, который помогает пользователям находить рецепты коктейлей. Бот может искать коктейли ингредиентам.

## Стек технологий

- **Java 18**
- **Spring Boot**
- **Telegram Bots API**
- **MongoDB**
- **Docker**
- **GitHub Actions** (для CI/CD)
- **LibreTranslate** (для перевода)

---

## Архитектура и развертывание

Проект полностью контейнеризирован с помощью Docker и управляется через `docker-compose`. Это обеспечивает легкий запуск всего стека приложений (бот, база данных, сервис перевода) одной командой.

### CI/CD (Непрерывная интеграция и доставка)

В проекте настроен полный цикл CI/CD с использованием GitHub Actions и Docker Hub.

1.  **Push в `main`**: Каждый коммит, отправленный в ветку `main`, автоматически запускает workflow на GitHub.
2.  **Сборка**: GitHub Actions собирает Spring Boot приложение в production-ready Docker-образ, используя многоэтапный `Dockerfile`.
3.  **Публикация**: Собранный образ публикуется в Docker Hub с тегом `latest`.
4.  **Автоматическое обновление**: На сервере (или локальном ПК) сервис **Watchtower** отслеживает обновления образа в Docker Hub. При появлении новой версии он автоматически скачивает ее и перезапускает контейнер с ботом.

Это обеспечивает бесшовную доставку обновлений без ручного вмешательства.

---

## Запуск проекта локально

Для запуска проекта на вашем компьютере должны быть установлены **Docker** и **Docker Compose**.

1.  **Клонируйте репозиторий:**
    ```bash
    git clone https://github.com/ArtemBurlachkov/telegram-bot.git
    cd ваш-репозиторий
    ```

2.  **Создайте файл `.env`:**
    В корне проекта создайте файл `.env` для хранения секретных ключей. `docker-compose` автоматически подхватит их.

    ```env
    # Имя вашего бота в Telegram
    TELEGRAM_BOT_NAME=MyCocktailBot

    # Токен, полученный от @BotFather в Telegram
    TELEGRAM_BOT_TOKEN=123456:ABC-DEF1234567890

    # Имя пользователя в Docker Hub для скачивания образа
    DOCKER_USERNAME=your_dockerhub_username
    ```
    > **Важно:** Замените `your_dockerhub_username` на ваше имя пользователя в Docker Hub. Это необходимо, чтобы `docker-compose` мог скачать правильный образ.

3.  **Настройте `docker-compose.yml`:**
    Откройте файл `docker-compose.yml` и убедитесь, что в сервисе `telegram-bot` указан правильный образ:
    ```yaml
    services:
      telegram-bot:
        image: ${DOCKER_USERNAME}/telegram-bot:latest # <-- Убедитесь, что здесь ваше имя пользователя
    ...
    

4.  **Запустите все сервисы:**
    Выполните команду в корне проекта:
    ```bash
    docker-compose up -d
    ```
    Эта команда скачает все необходимые образы (включая ваш бот, MongoDB, LibreTranslate и Watchtower) и запустит их в фоновом режиме.

5.  **Остановка проекта:**
    ```bash
    docker-compose down
    ```

---

## Переменные окружения и секреты

### Локальный запуск (`.env` файл)

- `TELEGRAM_BOT_NAME`: Имя вашего бота.
- `TELEGRAM_BOT_TOKEN`: Токен вашего бота.
- `DOCKER_USERNAME`: Ваш логин в Docker Hub.

### CI/CD (GitHub Secrets)

Для работы workflows в GitHub Actions необходимо настроить следующие секреты в настройках репозитория (`Settings` > `Secrets and variables` > `Actions`):

- `DOCKER_USERNAME`: Ваш логин в Docker Hub.
- `DOCKER_TOKEN`: Токен доступа (Access Token) из Docker Hub с правами на запись.
- `QODANA_TOKEN`: Токен для отправки отчетов в Qodana Cloud.

```text
io.prj3ct.telegramdemobot
+-- TelegramBotApplication.java     // Главный класс приложения Spring Boot
+-- config                          // Пакет с конфигурационными классами
|   +-- AppConfig.java              // Общая конфигурация приложения (бины, RestTemplate)
|   +-- BotConfig.java              // Конфигурация для Telegram-бота (токен, имя)
|   +-- BotInitializer.java         // Инициализация и регистрация бота
|   +-- TranslationConfig.java      // Конфигурация для сервиса перевода
+-- dto                             // Data Transfer Objects (Объекты для передачи данных)
|   +-- Cocktail.java               // DTO для общего списка коктейлей
|   +-- CocktailDetails.java        // DTO для детальной информации о коктейле
+-- model                           // Модели данных (сущности для БД)
|   +-- CocktailCache.java          // Сущность для кеширования коктейлей в базе данных
+-- repository                      // Репозитории для работы с базой данных
|   +-- CocktailCacheRepository.java// Репозиторий для доступа к кешу коктейлей
+-- service                         // Сервисный слой с бизнес-логикой
    +-- CocktailApiClient.java      // Интерфейс для клиента API TheCocktailDB
    +-- CocktailApiDataParser.java  // Парсер для обработки JSON-ответов от API
    +-- CocktailDBService.java      // Основной сервис, управляющий логикой поиска и перевода коктейлей
    +-- CocktailDbClient.java       // Реализация клиента для TheCocktailDB API
    +-- ImageService.java           // Сервис для загрузки изображений
    +-- TelegramBot.java            // Основной класс, отвечающий за логику работы Telegram-бота
    +-- TranslationService.java     // Сервис для перевода текста через LibreTranslate API
    +-- command                     // Пакет для реализации команд бота (/start и т.д.)
```
