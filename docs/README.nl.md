# Veyra gebruiken

De huidige testkandidaat is **1.0.0-rc.2**, voor Android 9 of nieuwer. [Download de ARM64-APK](https://github.com/Tufein/Veyra/releases/download/v1.0.0-rc.2/Veyra-1.0.0-rc.2-arm64-v8a.apk), of kies een andere architectuur op de [releasepagina](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.2). Er is nog geen stabiele 1.0 of Play Store-release.

**Vanaf 0.5.0 blijft de sleutel gelijk.** Test 1.0.0-rc.2 als update zonder 0.5.0 vooraf te verwijderen. Voor 0.4.x en eerdere CI-alpha’s met een andere sleutel: exporteer eerst je gegevens en lees [ondertekening en migratie](signing-and-migration.md) vóór verwijderen.

## Je browser inrichten

- Open **Menu → Instellingen** of het instellingensymbool op de startpagina. De instellingen hebben een eigen scherm met zoeken en terugnavigatie.
- **Uitstraling** kiest systeem/licht/donker, wallpaperkleuren of eigen accenten. **Startpagina** regelt je begroeting en compacte weergave.
- **Bibliotheek** combineert bladwijzers en startpaginakoppelingen. Vastzetten maakt een snelkoppeling; losmaken laat de bladwijzer bestaan. Bewerk gekoppelde namen/adressen op één plek. De startpagina heeft maximaal acht websites.
- **Lezen en zoeken** regelt tekstgrootte en zoekaanbieder. Een eigen zoekadres gebruikt HTTPS en precies één `%s` in het querydeel, bijvoorbeeld `https://example.org/search?q=%s`.
- **Browser en gegevens** opent geschiedenis, downloads, websitevoorkeuren, wissen, backups, standaardbrowser, geheugengebruik en synchronisatie.

## Tabbladen en InPrivate

Het taboverzicht biedt **Nieuw normaal tabblad** en **Nieuw InPrivate-tabblad**. InPrivate heeft een eigen startpagina, herkenbare adresbalk, blijvende statusbalk en maskers in het overzicht. Via **Alle InPrivate-tabbladen sluiten** beëindig je na bevestiging alleen de privésessie.

Via het browsermenu kun je een tabblad dupliceren (met hetzelfde type) en het webadres kopiëren. **Normaal tabblad heropenen** herstelt maximaal vijf gesloten normale pagina’s uit deze sessie. Privétabs worden nooit bijgehouden; geschiedenis verwijderen wist de heropenmogelijkheid. Met een toetsenbord: Ctrl+T opent hetzelfde type als de huidige tab, Ctrl+Shift+P opent InPrivate, Ctrl+Shift+T heropent een normale pagina vanuit normaal browsen.

## Gegevens en privétabs

Geschiedenis blijft lokaal en is doorzoekbaar/verwijderbaar. Privétabs gebruiken aparte Gecko-private sessies en komen niet in de normale geschiedenis/tabbackup. Zelf bewaarde bestanden, bladwijzers en gekopieerde tekst kunnen blijven bestaan. Android Autofill en gebruikersuitbreidingen zijn in privétabs uitgeschakeld. Dit maakt je netwerkverkeer niet anoniem. De [privacyhandleiding](privacy.md) beschrijft opslag en grenzen.

Bij een download kies je de bestemming via Android. **Downloads** toont voortgang, annuleren en openen. Na onderbreking kun je het bronadres opnieuw openen; Veyra herhaalt geen eerdere formulier-POST. Een registratie vergeten wist het bestand niet.

**Importeren en exporteren** maakt een leesbare JSON-backup van bladwijzers/snelkoppelingen, met optioneel voorkeuren en normale tabadressen. Bewaar die privé. Cookies en wachtwoorden gaan niet mee. Import controleert de gehele inhoud en toont eerst een bevestiging. Maak bij een volle startpagina eerst ruimte.

Websitevoorkeuren beheert camera/microfoon/locatie per HTTPS-origin. Android vraagt daarnaast systeemtoestemming. Je kunt rechten resetten en hostgegevens wissen. Cookietellingen gelden voor normale cookies; het overzicht toont geen cookiewaarden of volledige schijfmeting.

## Wachtwoorden, extensies en sync

**Wachtwoorden en passkeys** opent rechtstreeks Androids Autofill-instellingen. Veyra gebruikt je bestaande provider en heeft geen eigen kluis. Passkeys via een andere browser blijven in die browser ingelogd; de sessie wordt niet overgedragen.

**Extensies** zoekt in de Firefox-catalogus voor Android. Controleer bron, rechten en gegevensverklaringen vóór installeren. Ondertekening blijft verplicht. Beheer ondersteunt aan/uit, verwijderen, handmatige updates en beschikbare popup/optiepagina’s. De echte compatibiliteit van uBlock Origin, Dark Reader en ClearURLs is nog niet op hardware bevestigd. Herstel kan alle gebruikersuitbreidingen uitschakelen zonder je bibliotheek te wissen.

**Versleutelde synchronisatie** werkt handmatig met een eigen HTTPS/WebDAV-bestandserver en herstelzin. Alleen bladwijzers en snelkoppelingen synchroniseren. Bewaar de herstelzin in je wachtwoordmanager: geen e-mailherstel. Conflicten vragen een keuze/bevestiging. [Servervoorwaarden en verwijderingen](sync.md) leggen het gedrag uit; Veyra levert geen gehoste accountdienst.

## Wat nog moet worden gecontroleerd

De kandidaat bouwt en automatische opslag-/UI-/beveiligingscontroles slagen. Dat bewijst geen dagelijkse bruikbaarheid op echte toestellen. Onder meer downloads met bestaande logins, private cookies na procesbeëindiging, echte APK-updates, twee-toestelsync, TalkBack en controllerbediening blijven open in de [apparaatmatrix](device-validation.md). Zie [roadmap](../ROADMAP.md) en [verificatie](verification-1.0-rc.2.md).

Rapporteer problemen via [GitHub Issues](https://github.com/Tufein/Veyra/issues), met app-/Android-/Gecko-versie, toestel en reproduceerstappen. Verwijder persoonlijke URLs, wachtwoorden, tokens en cookies uit logs of beelden.
