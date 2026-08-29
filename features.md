# Lide features

Lide is a lightweight Java Swing IDE for browsing a project directory and editing files with syntax highlighting.

## Project tree

- Open a directory via **File → Open Directory** to show its files in the left tree.
- **File → Open Recent** lists previously opened project directories (most recent first), persisted in `~/.lide/recent-projects.txt`. Missing directories are removed when selected; **Clear Recent Projects** wipes the list.
- **File → New File** (Ctrl+N) creates an empty file in the selected tree folder (or the project root if nothing is selected) and opens it in the editor. Nested names such as `src/Hello.java` create missing parent folders. A project directory must be open first.
- Double-click a file to open it in the editor.
- Right-click a file for a context menu with **Open**, **Rename**, and **Copy Path**.
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

## Editing

- The editor shows line numbers and applies deferred syntax highlighting for supported languages (Java, JavaScript/JSX, TypeScript, Go, Python, XML/HTML, and plain text).
- Binary files (NUL bytes, non-text content, or undecodable UTF-8) open in a read-only hex dump viewer instead of the text editor, with offset, hex, and ASCII columns.
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
- **Build** (F5) compiles sources; **Test** (F6) compiles and runs unit tests; **Release** packages a JAR; **Download Dependencies** (F4) fetches the JDK and JARs listed in the INI; **Clear** deletes the build directory. F4, F5, and F6 work from anywhere in the window (editor, project tree, or scripts panel), not only when the Ladle menu is open.
- Commands run as `java -jar lib/ladle.jar <command> build.ini` with the project root as the working directory. If `JAVA_HOME` is unset, Lide supplies the detected JDK so `$JAVA_HOME` in `build.ini` still works. Output streams into the scripts panel console; Stop cancels the Ladle process and anything it spawned. Unsaved editors are saved first.
- Menu items are enabled whenever a project directory is open (and no command is already running). Choosing an item on a project that is missing Ladle shows an explanation instead of leaving the menu greyed out.

## Window chrome

- Dark IntelliJ-inspired theme for the tree, tabs, editor, and menus.
- Menus use a compact layout without the Windows checkmark gutter on the left.
- Enabled buttons use a brighter label, lighter fill, and stronger border; disabled buttons use a faded label, darker fill, and a muted border so the two states are easy to tell apart. Menu text uses the normal editor foreground so it stays readable; disabled menu items use a dimmer gray.
- Status bar shows the active file path, language, and modified state.
- Application icons appear on the window and taskbar; run `create-launcher.bat` to generate `assets/lide.ico` and a `Lide.lnk` shortcut that launches the IDE with that icon.
- **View → About Lide** opens an information dialog showing the application icon (64×64, taken from the same icon set used for the window) next to a short description of the IDE.
