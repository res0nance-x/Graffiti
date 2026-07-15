package gui

import gui.NativeResources.libDir
import java.io.File

/**
 * Resolves the `lib/` directory that contains the app's native binaries
 * (`webview.dll`, `WebView2Loader.dll`, `FileSelector.exe`, …).
 *
 * Resolution order
 * ────────────────
 * 1. **Beside the JAR** – the deployed layout where `lib/` sits next to `graffiti.jar`.
 *    Determined from the running JAR's own location via [Class.getProtectionDomain].
 * 2. **Working directory** – `lib/` relative to the JVM's current working directory.
 *    This is the default for IntelliJ run-configurations, which set the project root as
 *    the working directory, where `lib/` already lives during development.
 *
 * Both paths are tested by checking for [MARKER] so neither candidate is accepted
 * unless the expected content is actually there.
 */
internal object NativeResources {
	/**
	 * The resolved `lib/` directory (contains `webview.dll`, `WebView2Loader.dll`, `FileSelector.exe`).
	 * Evaluated lazily once and cached.
	 */
	val libDir: File by lazy { resolveDir("lib", "webview.dll") }

	/**
	 * The resolved `web/` directory (contains `index.html` and static assets).
	 * Evaluated lazily once and cached.
	 */
	val webDir: File by lazy { resolveDir("web", "index.html") }

	/**
	 * The `.webview` user-data directory passed to WebView2 via the
	 * `WEBVIEW2_USER_DATA_FOLDER` environment variable.
	 *
	 * Placed alongside the install root (the parent of [libDir]) so the browser
	 * cache lives next to `graffiti.jar` — not buried in `%LOCALAPPDATA%` under
	 * a java-named folder. The directory is created on first access if absent.
	 */
	val webviewDataDir: File by lazy {
		File(libDir.parentFile, ".webview").also { it.mkdirs() }
	}

	/**
	 * Generic directory resolver.
	 *
	 * @param dirName   The subdirectory name to look for (e.g. `"lib"`, `"web"`).
	 * @param marker    A file that must exist inside that directory to confirm it is valid.
	 */
	private fun resolveDir(dirName: String, marker: String): File {
		// ── 1. Beside the JAR ────────────────────────────────────────────────
		// getProtectionDomain().codeSource.location points to the JAR when deployed,
		// or to the compiled-classes output folder when run inside the IDE.
		// In both cases we look for a sibling directory named `dirName`.
		val codeSource = NativeResources::class.java.protectionDomain?.codeSource?.location
		if (codeSource != null) {
			runCatching {
				val codeFile = File(codeSource.toURI())
				// If codeFile is the JAR itself, its parent is the install directory.
				// If it's the classes-output directory, its parent is one level up —
				// but that path usually won't contain the marker, so we fall through cleanly.
				val candidate = File(codeFile.parentFile, dirName)
				if (File(candidate, marker).exists()) return candidate
			}
		}
		// ── 2. Working-directory fallback (IDE / custom launch) ───────────────
		val cwdCandidate = File(dirName).absoluteFile
		if (File(cwdCandidate, marker).exists()) return cwdCandidate

		val devCandidate = File("../GraffitiCore/$dirName").absoluteFile
		if (File(devCandidate, marker).exists()) return devCandidate

		error(
			"Could not locate the '$dirName' directory.\n" +
					"Expected '$dirName/$marker' either:\n" +
					"  • beside the running JAR, or\n" +
					"  • in the working directory (${File(".").absolutePath}), or\n" +
					"  • in the development directory (${devCandidate.absolutePath})"
		)
	}
}

