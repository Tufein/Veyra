# Veyra voor Android · 0.2.0

**Het web. Op jouw tempo.**

Een native Android-browser met een vernieuwde startpagina, afgeronde panelen, zachte kleurovergangen, bediening onderin en persoonlijke instellingen. Veyra is een werknaam; merk- en domeinbeschikbaarheid zijn niet onderzocht.

## Nieuw in 0.2.0

- Donker, licht of het Android-systeemthema; vier accentkleuren.
- Ruimtelijke of compacte startpagina, persoonlijke begroeting en maximaal acht bewerkbare snelkoppelingen.
- Tekstgrootte op websites: 85%, 100%, 115%, 130% of 150%.
- Zoeken met DuckDuckGo, Google, Brave Search of Ecosia.
- Ingebouwde adblocker met 56.339 domeinen plus de bestaande basisregels, enkele cosmetische regels en uitzonderingen per exacte websitehost.
- Keuze tussen voorkeur voor HTTPS en alleen HTTPS. Via de verbindingsindicator kun je een pagina expliciet met HTTPS proberen of, als de voorkeur dat toestaat, met HTTP openen.
- Android Autofill voor je bestaande wachtwoordmanager, een knop om invullen op te vragen en uitleg voor het kiezen van je provider in Android.
- Een expliciete route om een HTTPS-inlogpagina in een andere browser te openen voor externe passkeys.

Deze versie behoudt de adresbalk, terug/vooruit, vernieuwen, maximaal twaalf tabs, lokale bladwijzers, delen, zoeken op pagina en bestanden kiezen voor uploads uit 0.1.0. Er blijven maximaal drie WebViews in het geheugen. Oudere tabs kunnen opnieuw laden en formulierinhoud verliezen.

## Installeren als update

Open de test-APK op een telefoon met Android 9 (API 28) of hoger. Versie 0.2.0 heeft hetzelfde pakket (`nl.veyra.browser`), een hoger versienummer en dezelfde debughandtekening als de eerder geleverde 0.1.0. Installeer hem als update over de bestaande app om lokale tabs, cookies en bladwijzers te behouden.

Open **Menu → Veyra aanpassen** voor je voorkeuren. De knop rechtsboven op de startpagina opent dezelfde instellingen. Gebruik **Menu → Adblocker voor deze website** voor een website-uitzondering en **Menu → HTTPS en verbinding** voor de protocolkeuze.

## Wachtwoorden en passkeys

Veyra is geen eigen wachtwoordkluis en leest geen opgeslagen wachtwoorden of passkeys uit andere apps uit. Inlogvelden worden aan Android Autofill aangeboden; je geselecteerde provider beslist welke gegevens bij de website horen en vraagt eventueel ontgrendeling. Ondersteuning verschilt per provider, WebView-versie en website. Je stelt de manager in via Androids menu voor wachtwoorden, passkeys en automatisch invullen; de Veyra-knop opent de algemene Android-instellingen, omdat de precieze instellingenroute per telefoon verschilt.

Passkeys voor willekeurige websites in een eigen WebView-browser vereisen goedkeuring van de credential-provider voor die browser. Deze debugversie heeft die goedkeuring niet en implementeert geen JavaScript-bridge om dat te omzeilen. **Passkey-login gebeurt daarom in een andere geïnstalleerde browser met passkey-ondersteuning. De login blijft daar; de sessie wordt niet terug naar Veyra gekopieerd.** De gebruiker kiest expliciet de externe browser. Er worden geen wachtwoorden geïmporteerd, geëxporteerd of naar het klembord gekopieerd.

Officiële achtergrond: [Android Autofill](https://developer.android.com/identity/autofill) en [Credential Manager voor privileged apps](https://developer.android.com/training/sign-in/privileged-apps).

## HTTPS en adblocker

Getypte domeinen gebruiken standaard HTTPS. ‘Voorkeur HTTPS’ laat expliciete HTTP-links toe, maar valt bij een HTTPS-fout nooit automatisch terug naar HTTP. ‘Alleen HTTPS’ weigert HTTP-paginanavigatie en onderschept gewone HTTP-resourceverzoeken van WebView. Gemengde inhoud staat altijd uit. Dit is geen apparaatbrede firewall en verandert geen netwerkverkeer van andere apps. Certificaatfouten worden niet omzeild.

Bij wijzigen van de HTTPS-modus worden bestaande WebViews vrijgegeven om oude HTTP-pagina's uit de navigatiecache te verwijderen. Tabadressen en cookies blijven bewaard; de navigatiegeschiedenis in de tabs wordt opnieuw opgebouwd.

De adblocker leest een gebundelde lokale hosts-lijst op een achtergrondthread, met de oorspronkelijke basisregels als eerste vangnet. De lijst wordt niet automatisch bijgewerkt. Hij blokkeert verzoeken naar bekende advertentie- en trackinghosts en verbergt enkele bekende advertentievakken in de hoofdpagina. Hij blokkeert geen hoofdnavigeerdoel en garandeert geen blokkering van alle advertenties, bijvoorbeeld YouTube-videoadvertenties of advertenties die door hetzelfde domein als de website worden geserveerd. WebSockets, serviceworker-verkeer en dynamische regels van volledige browserextensies vallen buiten deze eenvoudige implementatie. De teller telt verzoeken in de nog open tabs, geen unieke trackers of gemeten tijdwinst.

De bronlijst, attributie en licenties staan in de APK-assets en in [docs/third-party.md](third-party.md). Twee bronsecties met uitsluitend niet-commerciële voorwaarden zijn weggelaten.

## Ontwikkelen en controleren

Open de projectmap in Android Studio en laat Gradle synchroniseren. Of gebruik JDK 17/21 en een Android SDK met platform 35:

```sh
export ANDROID_HOME=/pad/naar/Android/Sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

De Gradle Wrapper downloadt de bouwtools. Een proxy kan lokale Gradle-instellingen nodig maken. Het SDK-pad kan ook via een niet meegeleverde `local.properties` worden opgegeven.

De app gebruikt Java en native Android Views zonder UI-frameworkbibliotheken. De renderengine is de geïnstalleerde Android System WebView, gebaseerd op Chromium. Houd die via de Play Store bijgewerkt. Dit is geen zelfstandige Chromium-fork; sommige websites weigeren aanmelden vanuit ingebedde browsers.

De aanvullende rooktest gebruikt Python en precies één verbonden testapparaat:

```sh
python3 tools/smoke_test.py
```

Zet de adblocker vooraf aan en kies ‘Voorkeur HTTPS’, omdat de fixture lokaal via HTTP wordt aangeboden. Het script maakt testtabs en een bladwijzer; bestaande appgegevens worden niet gewist. `adb` moet in PATH staan of via de variabele `ADB` worden aangewezen. Screenshots komen in `app/build/smoke`. Zie [docs/verification-0.2.md](verification-0.2.md) voor de werkelijk uitgevoerde controles en grenzen.

## Overige grenzen en opslag

Deze versie heeft geen privémodus, downloadbeheer, synchronisatie, eigen wachtwoordkluis, passkeys rechtstreeks binnen Veyra, volledig scherm voor video of camera-, microfoon- en locatietoegang. Links naar telefoon, e-mail en kaarten vragen eerst of een andere app mag openen. Andere externe schema's, waaronder `intent:`, worden niet uitgevoerd.

Instellingen, tabadressen, bladwijzers, cookies, websiteopslag en de normale WebView-cache blijven lokaal op de telefoon. Voor alles wissen gebruik je Android → Apps → Veyra → Opslag → Gegevens wissen. OS-back-ups en apparaatoverdracht zijn uitgeschakeld. Er is geen eigen backend of toegevoegde analytics. Zoekopdrachten gaan naar je gekozen zoekdienst; websites, zoekdienst en WebView-provider verwerken hun eigen verkeer, waaronder eventuele Safe Browsing-verzoeken.

Een gemeten snelheidsvoordeel tegenover Samsung Internet is nog niet aangetoond. Zie [docs/benchmark.md](benchmark.md) voor een vergelijking op een echte telefoon.
