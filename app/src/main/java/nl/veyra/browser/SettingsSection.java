package nl.veyra.browser;

import java.text.Normalizer;
import java.util.Locale;

/** Stable destinations for deep links, restored navigation, and settings search. */
enum SettingsSection {
    OVERVIEW(R.string.settings_title, R.string.settings_intro, R.drawable.ic_tune, ""),
    APPEARANCE(R.string.settings_appearance, R.string.settings_appearance_summary, R.drawable.ic_palette,
            "thema donker licht systeem kleuren achtergrond material you accent mint lavendel blauw perzik"),
    HOME(R.string.settings_home, R.string.settings_home_summary, R.drawable.ic_home,
            "compact begroeting naam persoonlijk startpagina"),
    READING(R.string.settings_reading, R.string.settings_reading_summary, R.drawable.ic_search,
            "tekst lettergrootte zoom zoeken zoekmachine duckduckgo google brave ecosia lezen"),
    PRIVACY(R.string.settings_privacy, R.string.settings_privacy_summary, R.drawable.ic_shield,
            "advertenties adblock trackers blokkeren uitzonderingen websites veilig https privacy"),
    SHORTCUTS(R.string.settings_shortcuts, R.string.settings_shortcuts_summary, R.drawable.ic_link,
            "favorieten websites toevoegen bewerken verwijderen snelkoppelingen"),
    PASSWORDS(R.string.settings_passwords, R.string.settings_passwords_summary, R.drawable.ic_key,
            "wachtwoorden passkeys autofill invullen wachtwoordmanager bitwarden 1password"),
    EXTENSIONS(R.string.settings_extensions, R.string.settings_extensions_summary, R.drawable.ic_extension,
            "extensies extensions firefox addons add-ons uitbreidingen plugins"),
    BROWSER(R.string.browser_tools, R.string.browser_tools_summary, R.drawable.ic_home,
            "downloads geschiedenis standaardbrowser zoekmachine eigen export import backup synchronisatie geheugen gegevens cookies wissen"),
    ABOUT(R.string.settings_about, R.string.settings_about_summary, R.drawable.ic_info,
            "versie veyra alpha over browser"),
    SHORTCUT_EDITOR(R.string.shortcut_edit, R.string.shortcut_editor_summary, R.drawable.ic_link, "");

    final int title, summary, icon;
    private final String keywords;

    SettingsSection(int title, int summary, int icon, String keywords) {
        this.title = title; this.summary = summary; this.icon = icon; this.keywords = keywords;
    }
    boolean matches(String query, String titleText, String summaryText) {
        String haystack = normalize(titleText + " " + summaryText + " " + keywords);
        for (String word : normalize(query).trim().split("\\s+")) if (!haystack.contains(word)) return false;
        return true;
    }
    static SettingsSection restore(String value) {
        try { return value == null ? OVERVIEW : valueOf(value); }
        catch (IllegalArgumentException invalid) { return OVERVIEW; }
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
