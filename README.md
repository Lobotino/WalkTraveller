## Walk Traveller
### Android приложение для разметки и оценки маршрутов на карте цветом

<p align="center">
  <img src="https://github.com/user-attachments/assets/a57c60f5-67e3-4983-83a4-ba21a0ce8102" />
</p>

### Что делает?
- Записывает пути в реальном времени во время прогулки и сохраняет в локальную базу данных
- Позволяет во время записи трека в реальном времени выставить оценку пути цветом: 4 варианта от плохого к хорошему, или без цвета совсем
- Можно выставлять цвет кнопками громкости
- Можно смотреть записанные пути на карте в любом порядке, хоть все сразу
- Можно делиться записанными треками в виде локального файла

### Для чего?
У автора было желание иметь возможность составить карту "настроения" пеших маршрутов, чтобы их систематизировать и понять какие улицы на самом деле нравятся, а какие нет. Приложение помогает понять для себя где хорошо, а где плохо и почему конкретно.

На скриншоте выше нахоженная автором карта за 3 месяца ежедневных прогулок в Калининграде как пример удачного опыта :)

### Стек технологий
- Kotlin + Coroutines + Flow
- MVVM
- Sqlite + Room
- JUnit 4
- Open street map sdk
- Google GMS service location
- Из необычного, работа с AccessibilityService для выставления рейтинга по кнопкам громкости (см. VolumeKeysDetectorService)

### Что хочется сделать:
- Добавить поддержку работы с разными картами (2Gis, Yandex, Google)
- Добавить навигацию по городам. Сейчас нет возможности перемиститься на трек при его отображении, нужно знать где он находится
- Добавить фильтры треков по городам/способу передвижения/тёмное или светлое время суток и т.д.
- Добавить онбординг по переключению цвета на кнопки громкости. Иногда система забирает права на специальные возможности без предупреждения
- Добавить UI тесты

### Скачать
![card][card]

[card]: https://PlayBadges.pavi2410.me/badge/full?id=ru.lobotino.walktraveller
