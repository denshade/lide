# Lide features

Lide is a lightweight Java Swing IDE for browsing a project directory and editing files with syntax highlighting.

## Project tree

- Open a directory via **File → Open Directory** to show its files in the left tree. Dotfiles such as `.sdkmanrc` and `.gitignore` are listed. Build and VCS folders (`out`, `build`, `target`, `node_modules`, `.git`, `.idea`, `.svn`, `.hg`) are still skipped.
- An icon-only **Refresh** button (circular arrow, tooltip **Refresh**) at the top of the tree reloads the project from disk (new files and folders appear; removed ones disappear). It is disabled until a project directory is open. Right-click **Refresh** on a folder still reloads that folder only.
- **File → Open Recent** lists previously opened project directories (most recent first), persisted in `~/.lide/recent-projects.txt`. Missing directories are removed when selected; **Clear Recent Projects** wipes the list.
- **File → New File** (Ctrl+N) creates an empty file in the selected tree folder (or the project root if nothing is selected) and opens it in the editor. Nested names such as `src/Hello.java` create missing parent folders. A project directory must be open first.
- Double-click a file to open it in the editor.
- Right-click a file for a context menu with **Open**, **Rename**, and **Copy Path**.
- Right-click a `*Test.java` file also offers **Run Test**, which compiles and runs that one test class through Ladle, or through Gradle (`test --tests ClassName`) when the project is Gradle-only. Output streams into the scripts panel console (same as **Ladle → Test** or **Gradle → Test**). Unsaved editors are saved first. If the project's `lib/ladle.jar` is too old to accept a class filter, Lide uses a newer Ladle from its own `lib/` folder or a sibling Ladle distribution. On a project without Ladle or Gradle, or when no capable Ladle jar is found, an explanation dialog is shown.
- Right-click a folder for a context menu with **New File**, **Rename**, **Copy Path**, and **Refresh**. The open project folder itself cannot be renamed.
- **Rename** asks for a new name in the same folder. Open editor tabs follow the renamed file, or files under a renamed folder.
- **Copy Path** copies the absolute path to the clipboard. Editor tabs also offer **Copy Path** (disabled for untitled buffers).

## Editor tabs

- Each open file appears as a tab with a close (X) button.
- Click an inactive tab to switch to that file.
- Right-click a tab for a context menu with **Close**, **Close Others**, **Close All**, and **Copy Path**.
- Closing a dirty tab prompts to save, discard, or cancel.

## Navigation

- The **Navigate** menu offers **Back** (Alt+Left) and **Forward** (Alt+Right) to move through recently visited files in this session.
- Opening a file or switching to another open tab records it in the history. **Back** returns to the previous file (reopening the tab if it was closed). **Forward** undoes Back. Opening or switching to a different file after going back clears the forward list.
- **Navigate → Go to Class** (Shift twice) opens a search popup. Type a class name to filter Java, JavaScript/TypeScript, Go, and Python source files in the open project (build folders are skipped). Matches include prefix, substring, and camel-hump abbreviations such as `CN` for `ClassNavigator`. Enter or double-click opens the file at the class declaration. Esc cancels. The item is disabled when no project directory is open.
- **Navigate → To Test** (Ctrl+Shift+T) opens `ClassNameTest` for the current `ClassName` file (same extension, searched in the project). **Navigate → To Implementation** (Ctrl+Shift+I) does the reverse from `ClassNameTest`. Each item is disabled when the counterpart file is not found.

## Editing

- The editor shows line numbers and applies deferred syntax highlighting for supported languages (Java, JavaScript/JSX, TypeScript, Go, Python, XML/HTML, and plain text). Common text formats such as `.md`, `.txt`, `.csv`, `.json`, `.yml`, `.ini`, and similar always open in the text editor (UTF-8, UTF-16 with a BOM, or Windows-1252 when the file is not valid UTF-8).
- Known binary types (images, archives, class files, and similar) and other files whose content looks binary (NUL bytes or a high ratio of control characters) open in a read-only hex dump viewer instead of the text editor, with offset, hex, and ASCII columns.
- **Edit** menu supports **Undo** (Ctrl+Z), **Redo** (Ctrl+Y), **Copy** (Ctrl+C), and **Paste** (Ctrl+V) on the active tab.
- **Find** (Ctrl+F) opens a find bar on the active file; typing updates the highlighted match in the editor without taking focus from the search box. **Find Next** (F3) / **Find Previous** (Shift+F3) move between matches, with optional match-case and wrap-around. Esc closes the bar.
- **Find in Files** (Ctrl+Shift+F) searches the open project for text and lists matches in a dedicated bottom panel (file, line, and snippet). Click a result (or press Enter) to open that file and select the match. Match-case is optional. Build folders (`out`, `build`, `target`, and similar) and binary files are skipped. The panel starts minimized to a title bar and can be restored from the menu, the **+** button, or by clicking the title.
- **Ctrl+click** a class/type name to jump to its source file in the open project (uses imports and same-package lookup for Java; searches the project tree for a matching file name), including on Windows CRLF sources.
- Holding Ctrl over a navigable class name shows a hand cursor.
- **File → Save** / **Save All** (with keyboard shortcuts) write changes to disk.
- Unsaved buffers are marked with `*` in the tab title.

## Scripts panel

- A bottom panel lists runnable scripts found in the open project (`.bat`, `.cmd`, `.ps1`, `.sh`, `.bash`, `.py`), skipping `out` / `build` / `.git` and similar folders.
- Select a script and click **Run** (or double-click) to execute it with the project root as the working directory; output streams into the panel console in small batches so the IDE stays responsive during chatty or CPU-heavy commands. **Stop** kills the process and its child processes (compilers, test JVMs); **Refresh** rescans the tree. The console keeps a rolling buffer of recent output rather than growing without bound.

## Ladle

- The **Ladle** menu runs the project's Ladle build tool (a lightweight Java compiler/test/packager) when the open project contains `lib/ladle.jar` and `build.ini`.
- **Install Ladle** copies `lib/ladle.jar` and `bin/` launchers from a nearby Ladle distribution (a sibling `ladle` folder, or a folder you pick) into the open project. A starter `build.ini` is written only when the project does not already have one; `[javac].path` is the JDK that is running Lide, or `.jdk` (downloaded by **Download Dependencies**) if no JDK is detected.
- **Build** (F5) compiles sources; **Test** (F6) compiles and runs unit tests; **Release** packages a JAR; **Download Dependencies** (F4) fetches the JDK and JARs listed in the INI; **Clear** deletes the build directory. F4, F5, and F6 work from anywhere in the window (editor, project tree, or scripts panel), not only when the Ladle menu is open. Right-click **Run Test** on a `*Test.java` file runs only that class.
- Commands run as `java -jar lib/ladle.jar <command> build.ini` with the project root as the working directory. If you set **Java → Set JAVA_HOME**, that JDK is used; otherwise if `JAVA_HOME` is unset, Lide supplies the detected JDK so `$JAVA_HOME` in `build.ini` still works. Output streams into the scripts panel console; Stop cancels the Ladle process and anything it spawned. Unsaved editors are saved first.
- Menu items are enabled whenever a project directory is open (and no command is already running). Choosing an item on a project that is missing Ladle shows an explanation instead of leaving the menu greyed out.

## Gradle

- The **Gradle** menu runs the project's Gradle build when the open project contains `build.gradle`, `build.gradle.kts`, `settings.gradle`, or `settings.gradle.kts`. There is no Install item: Gradle is not copied into the project the way Ladle is.
- **Build** compiles main sources (`classes`); **Test** runs unit tests; **Release** packages outputs (`assemble`); **Download Dependencies** refreshes and lists the dependency tree; **Clear** deletes build outputs (`clean`). These are the Gradle counterparts of the Ladle menu. F4, F5, and F6 still belong to Ladle when the project has Ladle; on a Gradle-only project they run the matching Gradle tasks instead.
- Commands prefer the Gradle wrapper (`gradlew.bat` on Windows, `gradlew` elsewhere) and fall back to `gradle` on PATH. They run with `--console=plain` and the project root as the working directory. Gradle is started on a JDK that wrapper version can run on (Java 26 needs Gradle 9.4+). If `JAVA_HOME` is too new, Lide looks for a compatible JDK from **Java → Set JAVA_HOME**, `Program Files`, and similar locations. If none is found, an explanation dialog is shown instead of Groovy's "Unsupported class file major version" error. Output streams into the scripts panel console; Stop cancels the Gradle client process and anything it spawned. Unsaved editors are saved first. Right-click **Run Test** on a `*Test.java` file runs `test --tests` for that class when Ladle is not available.
- Menu items are enabled whenever a project directory is open (and no command is already running). Choosing an item on a project that is missing Gradle shows an explanation instead of leaving the menu greyed out.

## Java

- The **Java** menu sets the JDK Lide uses for **Ladle** and **Gradle** (`JAVA_HOME`, and that JDK's `bin` on `PATH`). This is an IDE setting, not per-project, and is stored in `~/.lide/java-home.txt`.
- **Set JAVA_HOME…** asks for a folder that contains `bin/javac` (picking `bin` itself is accepted and stored as the parent). **Clear JAVA_HOME** removes the override so Lide falls back to the process `JAVA_HOME` or the JDK running the IDE. The current path is shown at the top of the menu.

## Window chrome

- Dark IntelliJ-inspired theme for the tree, tabs, editor, and menus.
- Menus use a compact layout without the Windows checkmark gutter on the left.
- Enabled buttons use a brighter label, lighter fill, and stronger border; disabled buttons use a faded label, darker fill, and a muted border so the two states are easy to tell apart. Menu text uses the normal editor foreground so it stays readable; disabled menu items use a dimmer gray.
- Status bar shows the active file path, language, and modified state.
- Recovered failures (I/O errors, look-and-feel setup, and similar) are appended to `~/.lide/lide.log`.
- Application icons appear on the window and taskbar; run `create-launcher.bat` to generate `assets/lide.ico` and a `Lide.lnk` shortcut that launches the IDE with that icon.
- Run `package.bat` (Windows) or `package.sh` (macOS) to build a standalone application image with a bundled Java runtime. Output is `dist/Lide/Lide.exe` on Windows and `dist/Lide.app` on macOS. Each image can only be built on its own operating system, and a JDK that includes `jpackage` (JDK 16+) is required. Unsigned macOS apps may need a Gatekeeper exception on first launch. GitHub Actions workflow **Package** (`workflow_dispatch`, or a `v*` tag) builds both platform images and uploads them as artifacts.
- **View → About Lide** opens an information dialog showing the application icon (64×64, taken from the same icon set used for the window) next to a short description of the IDE.
