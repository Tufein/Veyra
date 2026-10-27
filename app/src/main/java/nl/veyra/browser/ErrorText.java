package nl.veyra.browser;

/** Localize domain errors without translating saved website names or addresses. */
final class ErrorText {
    private ErrorText() { }
    static String display(android.content.Context context,String value) {
        if(value==null)return "";
        if(!context.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("en"))return value;
        value=value.replace("Onderbroken doordat Veyra werd afgesloten. Kies opnieuw aanvragen; controleer het gedeeltelijke bestand.",context.getString(R.string.error_23));
        value=value.replace("Conflict: het serverbestand is ondertussen gewijzigd. Synchroniseer opnieuw; er is niets overschreven.",context.getString(R.string.error_15));
        value=value.replace("De samengevoegde backup heeft te veel websites. Maak eerst ruimte op de startpagina.",context.getString(R.string.error_06));
        value=value.replace("Android kon de download niet op de voorgrond starten. Controleer het doelbestand.",context.getString(R.string.error_25));
        value=value.replace("De server biedt geen bruikbare versiecontrole (ETag). Er is niets overschreven.",context.getString(R.string.error_13));
        value=value.replace("Te veel websites na samenvoegen. Maak eerst ruimte op je startpagina.",context.getString(R.string.error_07));
        value=value.replace("De server verwijst door. Gebruik het definitieve HTTPS-bestandsadres.",context.getString(R.string.error_17));
        value=value.replace("Download onderbroken. Vraag het bestand opnieuw aan via de website.",context.getString(R.string.error_28));
        value=value.replace("De server heeft de toegang geweigerd. Controleer je inloggegevens.",context.getString(R.string.error_16));
        value=value.replace("Gebruik een volledig HTTPS-adres naar een synchronisatiebestand.",context.getString(R.string.error_11));
        value=value.replace("Download kon niet starten. Controleer het lege doelbestand.",context.getString(R.string.error_24));
        value=value.replace("Niet-ondersteunde of ongeldige instelling in de backup: ",context.getString(R.string.error_10));
        value=value.replace("Ongeldige website in de backup. Er is niets gewijzigd.",context.getString(R.string.error_09));
        value=value.replace("Het backupbestand is ongeldig. Er is niets gewijzigd.",context.getString(R.string.error_05));
        value=value.replace("Onbekend backupformaat of niet-ondersteunde versie.",context.getString(R.string.error_02));
        value=value.replace("Gebruik een herstelzin van minstens twaalf tekens.",context.getString(R.string.error_20));
        value=value.replace("De gegevens konden niet worden geëxporteerd.",context.getString(R.string.error_00));
        value=value.replace("De synchronisatieserver antwoordde met HTTP ",context.getString(R.string.error_18));
        value=value.replace(" Controleer het gedeeltelijke doelbestand.",context.getString(R.string.error_29));
        value=value.replace("Niet-ondersteunde synchronisatieversie.",context.getString(R.string.error_21));
        value=value.replace("Ongeldige of privé-tab in de backup.",context.getString(R.string.error_04));
        value=value.replace("Te veel tabadressen in de backup.",context.getString(R.string.error_03));
        value=value.replace("De backup bevat te veel websites.",context.getString(R.string.error_08));
        value=value.replace("Het bestand is groter dan 2 MB.",context.getString(R.string.error_01));
        value=value.replace("Synchronisatiebestand te groot.",context.getString(R.string.error_14));
        value=value.replace("Te veel synchronisatiegegevens.",context.getString(R.string.error_19));
        value=value.replace("Ongeldig synchronisatiebestand.",context.getString(R.string.error_22));
        value=value.replace("Doelbestand niet beschikbaar",context.getString(R.string.error_26));
        value=value.replace("Ongeldige servergegevens.",context.getString(R.string.error_12));
        value=value.replace("Onvolledige download",context.getString(R.string.error_27));
        value=value.replace("Geannuleerd",context.getString(R.string.error_30));
        return value;
    }
}
