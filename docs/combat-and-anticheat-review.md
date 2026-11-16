# Combat, movement и визуалы: обзор Ancient Alpha

Дата исследования: 15 ноября 2026. Сравнение основано на исходниках Ancient Alpha, публичных страницах разработчиков и changelog. Рейтинг скорости закрытых аур без одинакового сервера, пинга и конфигураций установить нельзя.

## Что добавлено

| Функция | Поведение |
| --- | --- |
| AutoPotion | Splash-зелья Strength, Speed, Fire Resistance, Healing, Regeneration, Resistance; порог HP, обновление эффектов, задержка, Only Ground, Only Combat, Only Hotbar. Может использовать уже находящееся в offhand зелье. |
| Replenish | Помнит предметы в хотбаре, заменяет заканчивающийся или опустевший стак большим совместимым стаком из инвентаря. Остаток сохраняется в исходном слоте. |
| AutoArmor | Выбирает броню по защите, Protection, toughness и прочности. Сохраняет старую броню, учитывает запрет снятия и Keep Elytra. |
| AutoMine | Работает только через включённый PacketMine: блоки ног противника и surround. Два блока при DoubleBreak; сохраняет основной блок для подтверждённого Rebreak. Ручной выбор имеет приоритет. |
| AutoWeb | Текущие и прогнозируемые позиции ног противника, опционально голова. Проверяет друзей, расстояние и доступную грань. |
| SelfTrap | Обсидиановая крыша, боковые блоки головы, опциональные нижние опоры. |
| CA AntiWeakness | Проверяет итоговый attack damage с эффектами и модификаторами предмета; подбирает эффективный предмет из хотбара перед ударом по кристаллу. |
| CA Lethal | Опционально допускает превышение Max Self Damage ради смертельного прогноза по цели без предмета DEATH_PROTECTION в обеих руках, если у себя защита действительно удерживается. Работает независимо от Min Damage / Lethal Override. |
| BreakESP | Блок и ник игрока в центре. DoubleBreak сохраняет до двух последних наблюдавшихся позиций с ограниченным временем жизни. |
| Tags: Totem Pops | Счётчик `[-N]` рядом с ником; сбрасывается при смерти и смене мира. |
| Notifier | Notifications, Chat или Both; попы тотемов, отдельный Self, Death для других игроков. Сообщения в чат локальные. |
| HUD: Totems | Сумма тотемов в слотах 0–35 и offhand, без двойного подсчёта mainhand. Элемент перетаскивается штатным редактором HUD. |

MultiTask исключён по просьбе пользователя. WaterSpeed и TargetStrafe не добавлялись.

AutoWeb и SelfTrap используют общий pipeline взаимодействий с AuraActionQueue: GCD, последняя отправленная серверу позиция и ротация, ray trace, расстояние, точная грань, порядок выбора слота и prediction sequence. Перед отправкой повторно проверяются мир, игрок, предмет, позиция, коллизии и приоритет действия. CrystalAura, AnchorAura, Surround и новые плейс-модули учитывают общие резервы позиций до подтверждения. Все плейсы проходят через штатный `MultiPlayerGameMode.useItemOn`: блок и расход предмета предсказываются Minecraft локально, а подтверждения аур читают известное серверное состояние. При отмене исходящего пакета локальное предсказание действия откатывается. Скрытый выбранный слот временно предоставляется штатному взаимодействию без изменения видимого хотбара.

Scaffold заменён адаптацией LiquidBounce Normal: позиция под ногами с динамической высотой при выключенном Keep Y, фиксированные Normal offsets, стабильная линия движения, предсказание края и stabilized targeting. Плейс проверяет актуальный луч и нативный BlockPlaceContext. Safe Walk сохраняет ручной прыжок. Слот, порядок событий, ожидание подтверждения и ограничения повторов адаптированы к Ancient. Точный commit, исходники, отличия от upstream, лицензии и проверки описаны в [scaffold-port.md](scaffold-port.md).

Инвентарные операции сериализованы, повторно проверяют снимки предметов и не заменяют удерживаемый инструмент PacketMine. При включённом Swap Bypass пауза ввода применяется также при отключённом InventoryMove. Отменённый локальными обработчиками click не считается выполненным; клиентское предсказание такого click откатывается.

### Практические ограничения

- Lethal выключен по умолчанию. Использует реальную геометрию даже при Ignore Terrain, минимум текущего и прогнозируемого урона, запас 0.5 HP, отсутствие hurtTime и защиту от повторного обмена сразу после собственного попа. AutoTotem учитывает короткое удержание защиты после принятого пакета удара. Скрытое HP, серверные модификации урона, новая экипировка и будущий свап противника не известны клиенту: гарантировать серверное убийство нельзя.
- Второй блок DoubleBreak у противника не передаётся отдельным универсальным пакетом. BreakESP показывает последние наблюдавшиеся позиции, а не достоверно читает внутреннее состояние чужого PacketMine.
- Death и Totem Pops доступны для игроков, о которых клиент получил события или состояние сущности. Выход из области загрузки не считается смертью; смерти неизвестных клиенту игроков по всему серверу не определяются.
- При Rebreak новый START по другой позиции уничтожает преимущество сохранённой позиции. AutoMine поэтому не заполняет освободившийся вторичный слот ценой уже подготовленного Rebreak.

## Сравнение с популярными клиентами

| Клиент и публичный источник | Подтверждённый ориентир | Что полезно для Ancient |
| --- | --- | --- |
| [Future](https://www.futureclient.net/) | Публично перечислены AutoArmor, AutoMine, AutoPot, AutoWeb, Replenish, SelfTrap, AutoTrap, HoleFill, CrawlTrap, BowAim. | Запрошенный набор закрывает большую часть базовых crystal-инструментов. Дальнейшие пробелы: HoleFill, AutoTrap/CrawlTrap, сопровождение расходников и ремонта. |
| [Mio](https://www.mioclient.me/) | Разработчики заявляют AutoCrystal, BedAura, настраиваемые визуалы и HUD. Полного открытого каталога и воспроизводимых замеров нет. | Полезны визуализация тактического урона, удобные пресеты и диагностика причины задержки ауры. Превосходство конкретной реализации ауры не подтверждено. |
| [Shoreline](https://shorelineclient.net/), [архив старого dev build](https://github.com/yourfinalmemory/Shoreline-Client) | Архив прямо предупреждает, что это старый проблемный dev build, не текущая закрытая версия. Официальная страница не была доступна через используемый поиск. | Автоматическое city/mining и согласованность crystal-комбата — полезный ориентир; свойства актуальной версии и заявления «лучше Future/Mio на Grim» остаются непроверенными. |
| [Nursultan Docs](https://docs.nursultan.fun/scripting-api-ru/skripty/extras/modules), [Shaders](https://docs.nursultan.fun/scripting-api-ru/skripty/ui/shaders) | Документированы управление AttackAura и настройками, расширяемый UI и GLSL через scripting API. | Пресеты sword PvP, расширение HUD и единые бинды; Ancient уже имеет развитый Material HUD, шейдеры и сменные ротации. Полного сравнения закрытых combat-алгоритмов нет. |
| [Minced: changelog 01.03.2026](https://t.me/s/mincedclient?before=4895), [декабрьские изменения](https://t.me/s/mincedclient?before=4695) | Объявлены AutoPotion, индикатор тотемов, AutoTrap, тестовый AutoCrystal, PopChams, Kill Effect, ShaderESP и серверные ротации. | Попы, тотем HUD и зелья теперь есть. Из визуалов ещё полезны PopChams и Kill Effect. Заявление разработчика об обходе не заменяет проверку на конкретном Grim. |
| [LiquidBounce](https://liquidbounce.net/docs/modules/movement/noslow), [проверка реальной игрой](https://liquidbounce.net/blog/article/kGx0c2qn) | Публичные модули и режимы с версиями AC; разработчики описывают тесты на Grim с проверкой flags/setbacks/kicks, включая vanilla-навигацию и элитры с фейерверками. | Практический ориентир — проверять реальный протокол и серверные результаты. Увеличение количества настроек само по себе не доказывает совместимость. |

### Приоритеты дальнейшего развития

1. **Crystal PvP:** HoleESP + HoleFill, AutoTrap/CrawlTrap, AutoXP/ремонт по прочности брони. FastExp уже есть, но полноценной автоматической политики ремонта нет.
2. **Визуалы:** подписи target/self damage на выбранном кристалле/якоре, PopChams, Kill Effect, история попов и причин отказа ауры. Trajectories не нужно добавлять заново: Predictions уже показывает полёт снарядов и время до падения.
3. **Sword PvP:** готовые профили существующих HitAura/AimAssist/TriggerBot, ротаций, shield break, critical timing и sprint reset; наглядный cooldown и причина пропуска удара. Эти основы уже есть, полезнее согласовать их поведение и интерфейс.
4. **Movement на simulation AC:** SafeWalk через vanilla sneak/остановку ввода, parkour через обычный jump, HoleSnap через управление WASD, маршруты элитры с обычными фейерверками. В проекте уже есть несколько Elytra-модулей, поэтому нужен профиль с проверенной vanilla-физикой. Текущий Step изменяет только клиентский STEP_HEIGHT: это не свидетельство обхода Grim.

Для строгих simulation-профилей исключены WaterSpeed, ускоренный TargetStrafe, произвольное изменение скорости/step height, timer-ускорение и подавление knockback без отдельно воспроизведённого исключения на конкретной версии AC.

## Что известно об актуальных bypass

Наличие режима с именем Grim или Intave доказывает существование реализации, но не её работоспособность на сегодняшней версии сервера. Для анализа проверены релиз Grim 2.3.74 от 21.10.2026 и текущие публичные исходники ветки 2.0. [Пост LiquidBounce от 12.11.2026](https://liquidbounce.net/blog/article/g1H0innT) сообщает о тестовом Grim 2.3.75 и Vulcan 2.9.7.29; отдельная официальная страница релиза Grim 2.3.75 через используемый источник не найдена.

| Подход / AC | Публичные данные | Вывод для текущего клиента |
| --- | --- | --- |
| Grim: NoSlow | [Документация LiquidBounce](https://liquidbounce.net/docs/modules/movement/noslow), обновлённая 11.11.2026, перечисляет Grim-2.3.60, Grim-2.3.64-MC1.8, Grim-2.3.71, InvalidHand; есть отдельные режимы для consume/block/bow. | Это конкретные существующие режимы с историческими целями. Работоспособность на 26.3 + Grim 2.3.74/75 из списка не следует. Не переносить как универсальный обход. |
| Grim: switch/reuse NoSlow, некорректный порядок действий | [PacketOrder PR #1678](https://github.com/GrimAnticheat/Grim/pull/1678) merged; описывает detection NCP NoSlow, switch NoSlow, части autoblock/keypearl. [2.3.72](https://github.com/GrimAnticheat/Grim/releases/tag/v2.3.72) включает новый набор PacketOrder. | Старые slot-спам рецепты требуют повторной проверки и не подходят в качестве базового pipeline. |
| Grim: NoSlow через invalid drop | [Коммит 48115eb от 20.03.2026](https://github.com/GrimAnticheat/Grim/commit/48115eb7aa22291cf2c7da349c8eaf8ea7e11cb1) прямо называется `fix noslow with invalid drop item packets`. | Есть публичное свидетельство фикса; использовать этот старый рецепт как актуальный нельзя. |
| Grim: инвентарь на ходу | [MultiActionsC](https://raw.githubusercontent.com/GrimAnticheat/Grim/2.0/common/src/main/java/ac/grim/grimac/checks/impl/multiactions/MultiActionsC.java) проверяет sprint, sneak на новых клиентах и knownInput.moving; [MultiActionsD](https://raw.githubusercontent.com/GrimAnticheat/Grim/2.0/common/src/main/java/ac/grim/grimac/checks/impl/multiactions/MultiActionsD.java) — закрытие инвентаря. [2.3.73](https://github.com/GrimAnticheat/Grim/releases/tag/v2.3.73) помечает C/D stable. | Перед click/close нужен согласованный idle input. Именно это учитывается новыми inventory helpers. |
| Grim: placement | [RotationPlace](https://raw.githubusercontent.com/GrimAnticheat/Grim/2.0/common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/RotationPlace.java) проверяет ray trace до/после flying. Публичный CheckManager также регистрирует PositionPlace, FarPlace, FabricatedPlace, MultiPlace и другие проверки. | Достоверная грань, ray trace, позиция и последовательность пакетов важнее названия bypass. Установка в другой руке не отменяет геометрию. |
| Grim: Blink / задержка транзакций / «Disabler» | [Обсуждение #2521](https://github.com/GrimAnticheat/Grim/pull/2521) различает TransactionOrder и TimerLimit; предложенная дополнительная проверка закрыта, не merged. | Closed PR не доказывает отсутствие существующих проверок и не подтверждает рабочий Disabler. В новые модули удержание транзакций не добавлено. |
| Grim: simulation, reach, скорость и KB | [Официальный README](https://github.com/GrimAnticheat/Grim) описывает replication мира, latency/inventory compensation и моделирование движения. [2.3.73](https://github.com/GrimAnticheat/Grim/releases/tag/v2.3.73) сообщает о фиксе reuse setback velocity и reach-регрессии. | Совместимая автоматизация обычного ввода перспективнее постоянного превышения vanilla-физики. Старый velocity/reach bypass не считать актуальным. |
| Intave | [14.9.7 от 24.09.2026](https://github.com/intave/intave/releases/tag/14.9.7): поддержка 26.3, block-interaction emulation, исправления click reconstruction, reach через смену слота, placement acknowledgements. LiquidBounce документирует NoSlow Intave-14.8.4. | Старое имя режима не подтверждает обход 14.9.7. Отдельно проверять взаимодействия, слоты и физику на целевой сборке. |
| Polar | [Официальный сайт](https://polar.top/) заявляет simulation, ML и cloud mitigations; алгоритмы закрыты. | Внешний ролик с ротацией не устанавливает универсальный обход актуальной модели. Для выводов нужен конкретный серверный тест. |
| Vulcan | [Официальные updates](https://www.spigotmc.org/resources/vulcan-anti-cheat-advanced-cheat-detection-1-8-26-2-folia-supported.83626/updates) описывают переработку Bad Packets D, packet limits и стабилизацию Speed E/Flight F в 2.9.7.18. Страница индексируется с rollback на 2.9.7.23; публичный тестовый сервер сообщает более новую 2.9.7.29. | Конфигурации старого Vulcan не переносить вслепую. Официальная страница также перечисляет несовместимость с Grim: их совместное включение не является чистым сравнительным стендом. |

В этом изменении реализованы совместимые взаимодействия и проверки контекста. Ни один новый exploit/bypass не объявляется подтверждённым без серверного QA.

## Проверка

- `./gradlew build`: успешно, Kotlin/Java компилируются; автоматических игровых тестов в проекте нет.
- Временная отдельная JVM-проверка: 26 сценариев Lethal и PacketMineProgress, включая конечность чисел, наличие защиты, порог lethal, подтверждение air и сохранение primed Rebreak. Тестовая инфраструктура в репозиторий не добавлена.
- Игровое QA на Grim/Vulcan/Intave/Polar не выполнялось.

Для ручной проверки: отдельный стенд с известными версиями AC/сервера, Minecraft 26.3 и сохранением flags/setbacks/kicks. Проверить каждый модуль отдельно, затем совместно с CA/AA/Surround/AutoTotem; движения и sneak во время инвентарных обменов; пинг 0/100/250 ms; DoubleBreak и повторную установку блока при Rebreak; смерть/респавн/смену мира; отсутствие и появление тотема в обеих руках цели; отменённые пакеты и неподтверждённые плейсы. FakePlayer полезен для локальной геометрии и UI, но не проверяет серверный античит.
