package io.hermes.remote.ui.theme

/**
 * Which colour mode the app should use.
 *
 * [SYSTEM] is the default because it is the one answer that is right on a
 * device the user has already configured: a phone that switches to light at
 * sunrise should drag the app with it. The explicit values exist for the case
 * where the two disagree — a dark-mode-everything user who wants a light chat,
 * or the reverse.
 */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT;

    /** Whether this mode resolves to the dark palette, given the OS setting. */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        DARK -> true
        LIGHT -> false
    }

    companion object {
        /** Parse a persisted value, falling back to [SYSTEM] for anything else. */
        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: SYSTEM
    }
}
