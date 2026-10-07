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
- **LaTeX math**: questions, choices and explanations are typeset with real math fonts (KaTeX), on both desktop and Android, with no WebView. See [Writing math](#writing-math).
- **Question bank**: add, edit, delete and search questions. Questions can have 2–8 choices, with one marked correct. The editor has:
  - a palette of LaTeX snippets (fractions, integrals, sums, limits, matrices, `\mathbb{R}`, Greek letters…), with each button showing the rendered symbol
  - a live preview of the question as it will appear
- **Import / export**: paste a JSON array to add many questions at once, or copy the whole bank to the clipboard.
- 39 sample questions written for this app, covering calculus, linear and abstract algebra, analysis, topology and more. They're loaded on first launch and can be restored from Import / export.

### Writing math

Write LaTeX between the usual Markdown/MathJax delimiters:

| Syntax | Result |
|---|---|
| `$…$` or `\(…\)` | inline math, flowing with the text |
| `$$…$$` or `\[…\]` | display math on its own centered line |
| `\$` | a literal dollar sign |

Example: `Evaluate $\displaystyle\int_0^{\pi} x \sin x \, dx$.`

Use `\displaystyle` for full-size integrals, sums and limits inline, and `\dfrac` for a full-size fraction. Text outside the delimiters is plain text, so Unicode symbols still work there.

Rendering uses [huarangmeng/latex](https://github.com/huarangmeng/latex) (MIT). Before rendering, the app adapts the source to TeX's math-mode rules: spaces are ignored and `-` becomes a real minus sign with operator spacing. See `normalizeMath` in [MathMarkup.kt](shared/src/ui/math/MathMarkup.kt).

### Import format

```json
[
  {
    "topic": "Calculus",
    "text": "$\\int_0^1 2x \\, dx =$",
    "choices": ["$0$", "$\\frac{1}{2}$", "$1$", "$2$", "$4$"],
    "answer": "C",
    "explanation": "$\\left[x^2\\right]_0^1 = 1$."
  }
]
```

Mark the right answer with either `"answer"` (a letter) or `"correctIndex"` (0-based). If an item has an `"id"` that matches an existing question, it replaces that question. Inside JSON strings, every LaTeX backslash must be doubled (`\\frac`).

### Cloud sync (Firebase)

Sign in on the **Account & sync** screen to keep your question bank and practice history in sync between devices. You can sign in with **Google**, with **email and password**, or **as a guest**. A guest is an anonymous account that backs up only the current device; signing in with Google or email later merges the guest's data into that account.

- **Google on Android** uses Credential Manager with the Web OAuth client ID. It only works once the signing certificate's SHA-1 is registered for the Android app in Firebase (Project settings → Your apps).
- **Google on desktop** opens the system browser and uses the OAuth loopback + PKCE flow. It needs a "Desktop app" OAuth client from Google Cloud console → APIs & Services → Credentials. Put its ID and secret in `FirebaseSecrets.kt` (see [Secrets](#secrets)). Google doesn't treat an installed app's client secret as confidential. While the ID is blank, the Google button is hidden on desktop. If the OAuth client is in a different Google Cloud project than Firebase, add its client ID under Firebase console → Authentication → Sign-in method → Google → **Safelist client IDs from external projects**.

The app keeps working offline: local files remain the primary copy. Changes upload a few seconds after you make them, and the app also syncs at startup and when you tap **Sync now**.

- **Backend:** Firebase project `grepractice-519d9`. It uses Cloud Firestore (Standard edition, `europe-central2`) and Firebase Authentication (Google, email/password, anonymous).
- **Android:** the official Firebase Auth and Firestore SDKs. Firebase is initialized in code from the values in `FirebaseSecrets.kt`, because this build doesn't run the Google Services Gradle plugin. Keep those values in sync with `androidApp/google-services.json`.
- **Desktop:** there's no official Firebase client SDK for desktop JVM, so it uses the official Firebase Auth and Firestore REST APIs instead ([CloudBackend.jvm.kt](shared/src@jvm/CloudBackend.jvm.kt)). The sign-in refresh token is stored in `~/.grepractice/session.json`.
- **Data layout:**
  - `bank/{id}` and `bankImages/{name}`: the shared question bank and its figures (see [Shared question bank](#shared-question-bank)).
  - `users/{uid}/questions/{id}`, `users/{uid}/results/{id}` and `users/{uid}/images/{name}`: each user's own questions, history and figures.

  Every document has three fields:
  - `payload`: the record as JSON (base64 for images)
  - `updatedAt`: when it was last edited (epoch millis)
  - `deleted`: a tombstone flag, so deletions reach other devices
- **Conflicts:**
  - Questions: the most recent edit wins.
  - Results: never change after they're saved, so they're merged, and a deletion on either side wins.
- **Security:** [firestore.rules](firestore.rules) makes the shared bank readable by anyone and writable only by admins. Each user can read and write only their own `users/{uid}` documents, and every document's shape is checked. To deploy the rules, either paste them into the Firebase console (Firestore → Rules), or run `firebase deploy --only firestore:rules` with the Firebase CLI. This project includes `firebase.json` and `.firebaserc` for the CLI.

### Shared question bank

Every user sees the common **shared bank**, even without signing in, plus any questions they add themselves. Their own questions stay private and sync only to their own devices. When a shared question and one of a user's own questions have the same ID, the shared one is shown.

- **Updates:** the app downloads only shared questions changed since its last fetch: at startup, after signing in, and on **Sync now**. It keeps a copy in `bank.json` so it works offline.
- **Admins** are the only users who can change the shared bank. In the question bank, an admin gets **Publish my questions to the shared bank**, which moves their own questions (and their figures) into the bank. An admin's edits and deletions of shared questions apply for everyone. Imported JSON whose IDs belong to the bank updates the bank when an admin imports it, and is skipped for anyone else.
- **Making someone an admin:** in the Firebase console, go to Firestore → Data, start a collection named `admins`, and add a document whose ID is the user's UID (Authentication → Users). The document doesn't need any fields. Nobody can create these documents from the app.

### Data

Questions and results are saved automatically as JSON:
- **Desktop:** in `~/.grepractice/` (`questions.json`, `results.json`)
- **Android:** in the app's private files directory

If a file can't be read, the app keeps a copy of it as `<name>.broken`.

## Project layout

- [shared/src](./shared/src): common code.
  - `model/`: data classes
  - `data/`: repository, persistence and sample questions
  - `data/cloud/`: the Firebase sync interface and the merge engine
  - `practice/`: the timed session engine
  - `ui/`: Compose screens
  - `ui/math/`: the `$…$` parser and the `MathText` renderer
- `shared/src@jvm`, `shared/src@android`: platform storage and back handling.
- [shared/test](./shared/test), [shared/test@jvm](./shared/test@jvm): unit tests.
- [desktopApp](./desktopApp), [androidApp](./androidApp): entry points.

## Secrets

Firebase and OAuth credentials are kept out of git. Two local files are git-ignored:

- `shared/src/data/cloud/FirebaseSecrets.kt`: Firebase project config and the OAuth client IDs and secret. To create it, copy [FirebaseSecrets.kt.example](shared/src/data/cloud/FirebaseSecrets.kt.example) next to it and fill in the values; the template says where each one comes from. The project won't compile without this file.
- `androidApp/google-services.json`: download it from Firebase console → Project settings → Your apps.

## Building and running

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts download the pinned toolchain the first time you run them. On Windows, run them from PowerShell or cmd. Git Bash's `tar` breaks the first-run bootstrap.

- Desktop app: `./kotlin run -m desktopApp`
- Android app: `./kotlin run -m androidApp`
- Tests: `./kotlin test --platform jvm`
