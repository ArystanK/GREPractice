# GRE Math Practice

A practice app for the GRE Mathematics Subject Test, for Android and desktop (JVM). It's written in Kotlin Multiplatform + Compose and built with the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

## Features

- **Simulated exam**: 66 questions in 170 minutes (≈2:34 per question), the same as the real test. Answers are revealed only at the end. If your bank has fewer than 66 questions, the exam gets shorter and the time limit shrinks to match.
- **Practice by topic**: choose topics, how many questions, and the timing (GRE pace, a custom limit, or untimed). You can also choose:
  - checking each answer as you go
  - shuffling the answer choices
  - picking questions you haven't seen or got wrong first
- **Random question**: one question at a time with a stopwatch, an instant check and the explanation. You can filter by topic.
- **Timing**: a countdown that submits the session when time runs out, plus the time spent on each question. Both are saved with the result.
- **Session tools**: a question navigator, flag-for-review, and keyboard shortcuts (`A`–`E`/`1`–`5` choose, `←`/`→` move, `F` flag, `Enter` check/next).
- **Results & history**: every session is saved with your answers. The review shows:
  - your answer next to the correct one
  - the explanation
  - time per question
  - a breakdown by topic

  You can filter the review (incorrect, unanswered, flagged) and retry the questions you missed with one click.
- **Stats**: accuracy and average time per topic across all sessions, shown on the home screen.
- **Question bank**: add, edit, delete and search questions. The editor has a palette of Unicode math symbols (∫ ∑ √ π ℝ ⊂ …). Questions can have 2–8 choices, with one marked correct.
- **Import / export**: paste a JSON array to add many questions at once, or copy the whole bank to the clipboard.
- 39 sample questions written for this app, covering calculus, linear and abstract algebra, analysis, topology and more. They're loaded on first launch and can be restored from Import / export.

### Import format

```json
[
  {
    "topic": "Calculus",
    "text": "∫₀¹ 2x dx =",
    "choices": ["0", "1/2", "1", "2", "4"],
    "answer": "C",
    "explanation": "x² from 0 to 1 is 1."
  }
]
```

Mark the right answer with either `"answer"` (a letter) or `"correctIndex"` (0-based). If an item has an `"id"` that matches an existing question, it replaces that question.

### Data

Questions and results are saved automatically as JSON:
- **Desktop:** in `~/.grepractice/` (`questions.json`, `results.json`)
- **Android:** in the app's private files directory

If a file can't be read, the app keeps a copy of it as `<name>.broken`.

## Project layout

- [shared/src](./shared/src): common code.
  - `model/`: data classes
  - `data/`: repository, persistence and sample questions
  - `practice/`: the timed session engine
  - `ui/`: Compose screens
- `shared/src@jvm`, `shared/src@android`: platform storage and back handling.
- [shared/test](./shared/test), [shared/test@jvm](./shared/test@jvm): unit tests.
- [desktopApp](./desktopApp), [androidApp](./androidApp): entry points.

## Building and running

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts download the pinned toolchain the first time you run them. On Windows, run them from PowerShell or cmd. Git Bash's `tar` breaks the first-run bootstrap.

- Desktop app: `./kotlin run -m desktopApp`
- Android app: `./kotlin run -m androidApp`
- Tests: `./kotlin test --platform jvm`
