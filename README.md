# Baking Buddy

Four kitchen timers on one Android screen. Built for baking, when the bread, the oven and the dough all need watching at once.

- **4 timers in a 2x2 grid.** Each one has its own name, color and end sound.
- **Fast to set.** Tap the digits for a min/sec wheel, or long-press them for a microwave-style number pad (`5 3 0` = 5:30). Use **−/+** for one minute less or more.
- **Start, pause, reset, clear.** Reset goes back to the last time you set.
- **Rings until you stop it.** Plays an alarm sound from the phone's sound picker, or speaks the timer's name with text-to-speech. The quiet gap between announcements is adjustable.
- **Reliable.** Exact alarms fire with the screen off, and running timers survive the app being closed.
- **Settings:** System/Light/Dark theme, timer size, hide the leading zero, alarm repeat gap.
- Scandinavian-style design: linen and charcoal themes, hairline borders, a muted Nordic palette.

Plain Java with no XML layouts and no libraries. minSdk 33 (Android 13+).

## Build

You need Docker. Android Studio is not required.

```sh
./android/build.sh        # -> android/BakingBuddy.apk
```

The APK is signed with a throwaway debug key unless `android/keystore.properties` exists
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; it is git-ignored).
You can also open `android/` in Android Studio.

## Code

| File | What |
|---|---|
| `Timers.java` | All timer state, behaviour and alarms |
| `TimersActivity.java` | The screen, settings and dialogs |
| `SegmentView.java` | Seven-segment mm:ss readout |
| `IconButton.java` | Flat line-drawn buttons |
| `AlarmReceiver.java`, `RingService.java` | Fire the alarm; play the sound or speak the name |

Icon sources are in `android/icon/` (`mk-icon.py` renders the mipmaps).

## License

MIT
