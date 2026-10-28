# Veyra — roadmap na 0.5.0

**Basis: de gepubliceerde 0.5.0-alpha.** De volgende stap is een betrouwbare test- en updateronde. Daarna volgen verdere vormgeving, aantoonbare extensiecompatibiliteit en afronding van privacy, synchronisatie en handheldgebruik. Versienummers geven de werkvolgorde aan; er zijn nog geen vaste releasedatums.

[Download 0.5.0](https://github.com/Tufein/Veyra/releases/tag/v0.5.0) · [Verificatie](docs/verification-0.5.md) · [Apparaatmatrix](docs/device-validation.md) · [Oorspronkelijke roadmap en criteria](docs/roadmap-0.5-baseline.md)

## Voortgang naar 1.0

Op verzoek is de huidige code voorbereid als **1.0.0-rc.2**. De [kandidaat is gepubliceerd](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.2) en bundelt release-optimalisatie, correcties voor imports/downloads/site-rechten/sync en uitgebreidere software- en publicatiecontroles. Rc.2 voegt zichtbare InPrivate-status, een privé-startpagina, privacybewust dupliceren, normaal heropenen, webadres kopiëren en bevestigd sluiten van alle privétabs toe. [Verificatie](docs/verification-1.0-rc.2.md) en het [vrijgaveverslag](docs/release-readiness.json) houden de nog open toestel- en beheercriteria bij. De onderstaande werkpakketten en 1.0-voorwaarden blijven gelden; een kandidaatversie sluit ze niet automatisch af.

## Huidige stand

| Onderdeel | Wat er is | Wat nog openstaat |
| --- | --- | --- |
| Vormgeving | Eigen Material You-instellingen, gedeelde bibliotheek, menu- en tabpanelen, tabletindeling, NL/EN | Controle en verfijning op echte schermen, TalkBack en controller |
| Dagelijks browsen | Geschiedenis, downloads, backups, zoekvoorkeuren, browserrol, normale tabadressen herstellen, dupliceren/kopiëren en tijdelijk normaal heropenen | Ingelogde/onderbroken downloads, formulierbehoud en langdurig toestelgebruik |
| Privacy | Gecko-privétabs met blijvende InPrivate-aanduiding, afzonderlijk wissen, sitepermissies en normale cookietellingen | Isolatie na crash/procesbeëindiging en gerichte verwijdering op hardware |
| Extensies | Firefox-catalogus, ondertekende installatie, rechten, updates en herstelbeheer | Werkelijk gedrag van uBlock Origin, Dark Reader en ClearURLs |
| Synchronisatie | Handmatige versleutelde bladwijzer-/snelkoppelingensync met eigen HTTPS-server | Twee echte clients, serverconflicten, offline/herstel en onafhankelijke beveiligingsreview |
| Releases | Drie publieke APK’s, vaste certificaatvingerafdruk en checksums; publicatie via Actions geslaagd | Onafhankelijke sleutelbackup, geautomatiseerde rebuilds en echte update met gegevensbehoud |

De actuele aantallen en software-/publicatiebewijzen staan in het [verificatieverslag](docs/verification-1.0-rc.2.md). Ze staan los van de nog niet uitgevoerde apparaatcontroles.

## Nieuwe werkvolgorde

| Mijlpaal | Prioriteit | Resultaat voor de gebruiker | Vrijgavevoorwaarde |
| --- | --- | --- | --- |
| **0.5.1 — Testen en betrouwbaar bijwerken** | P0 | Problemen uit de eerste telefoontest oplossen; vaste updates zonder gegevensverlies | Werkelijke installatie/update, backup/import en basissmoketest vastgelegd |
| **0.6 — Vormgeving en bibliotheek verder afwerken** | P1 | Consistentere startpagina, instellingen, menu’s, tabs en opgeslagen websites | Geteste hoofdflows in beide thema’s, op klein scherm en met groot lettertype |
| **0.7 — Extensies met bewezen ondersteuning** | P1 | Zichtbaar welke extensies en functies werkelijk werken, met herstelbare fouten | Drie extensies doorlopen de afgesproken toestelscenario’s |
| **0.8 — Privacy, sessies en synchronisatie afronden** | P1 | Betrouwbare privésessies, gegevens wissen, downloads en samenvoegen tussen apparaten | Hardwarebewijs voor isolatie/verwijdering, overdrachten en tweeclientsync |
| **0.9 — Beta voor dagelijks en handheldgebruik** | P1 | Gemeten prestaties, toegankelijke bediening en brede sitecompatibiliteit | Apparaatmatrix, benchmarks en toegankelijkheidscontrole afgerond |
| **1.0 — Stabiele browser** | Vrijgavepoort | Betrouwbare installatie, updates en dagelijks gebruik | Alle onderstaande 1.0-criteria aantoonbaar behaald |

Ernstige opstart-, navigatie-, privacy- of gegevensverliesproblemen onderbreken deze volgorde en krijgen eerst een correctierelease. Nieuwe functies worden per ronde begrensd zodat de bestaande browser bruikbaar blijft.

## 0.5.1 — Testen en betrouwbaar bijwerken

- De 0.5.0-alpha op een echte telefoon testen: starten, navigeren, tabwissel, herstart, geschiedenis, bibliotheek, instellingen en één download.
- Oude alpha-gegevens veilig exporteren en importeren; geen verwijdering adviseren voordat de backup is gecontroleerd.
- De bestaande vaste sleutel veilig behouden en onafhankelijk versleuteld back-uppen. De sleutel niet opnieuw vervangen.
- Repository signing secrets correct provisioneren voor automatische builds. De huidige Secrets API-weigering (403) oplossen met passende toegang; de sleutel blijft uit Git en publieke artifacts.
- Een werkelijke update van 0.5.0 naar de volgende vaste-key-APK uitvoeren met behoud van instellingen, bladwijzers, snelkoppelingen en normale tabadressen.
- De releaseprocedure reproduceerbaar maken: certificaat, pakketnaam, versiecode, ABI, checksums en openbare downloadlinks controleren. Actions-afhankelijkheden actualiseren en opnieuw valideren.
- Kritieke fouten uit de eerste test herstellen en de bekende beperkingen bij de release zetten.

**Klaar wanneer:** een echte installatie en update slagen, gegevens behouden blijven en het verslag toestel, Android-versie, APK-versie en werkelijk uitgevoerde stappen bevat. Een lokale build of publicatiecontrole alleen sluit deze mijlpaal niet af.

## 0.6 — Vormgeving en bibliotheek

- Startpagina, adresbalk, bodemmenu, tabpanelen en alle instellingen dezelfde afstanden, kleurrollen, pictogrammen en typografie geven.
- Overbodige ruimte, onduidelijke labels en afgekapt scherminhoud aanpakken op basis van de eerste telefoontest.
- De bibliotheekflow verfijnen: bewaren, vastzetten, bewerken, ordenen en verwijderen moeten voorspelbaar blijven. Gekoppelde namen/adressen blijven afgestemd.
- Zoeken, lege toestanden, grote verzamelingen, importresultaten en terugnavigatie verder afwerken; gebruikersinvoer blijft bij rotatie behouden.
- De belangrijkste instellingen ook prettig maken op tablets, landscape en in venstermodus.
- Aanraakgebieden van minimaal 48 dp, zichtbare focus en bruikbaar contrast controleren. Test systeem/licht/donker, wallpaperkleuren, 320 dp en 200% tekst.

**Klaar wanneer:** de hoofdflows visueel en functioneel op een echt toestel zijn gecontroleerd en de regressietests blijven slagen. Mockups en Robolectric-renderingen krijgen hun eigen label als ontwerpbewijs.

**Eventuele uitbreiding na deze basis:** mappen, meervoudige selectie en slepen voor volgorde eerst als afzonderlijke productkeuze beoordelen. Ze zijn nog niet geïmplementeerd en geen voorwaarde voor deze ronde.

## 0.7 — Extensies met bewezen ondersteuning

- De [compatibiliteitsmatrix](docs/extensions.md) invullen met echte resultaten voor uBlock Origin, Dark Reader en ClearURLs, inclusief versies en toestel/engine.
- Installeren, toestemming weigeren, aan/uit, verwijderen, herstart, browseractie, popup en opties testen.
- `tabs.create`, activatie, ondersteunde updates en sluiten controleren; niet-ondersteunde API’s duidelijk blijven afwijzen.
- Een echte update met extra rechten zowel weigeren als toestaan. Fouten en opnieuw proberen begrijpelijk maken.
- Herstel/alles uitschakelen testen zonder bibliotheekverlies; wisselwerking met de ingebouwde blokkering controleren.
- Native messaging beperkt houden tot de eigen beschermingsmodule en gebruikersuitbreidingen buiten privétabs houden zolang daarvoor geen afzonderlijk veilig ontwerp is gevalideerd.

**Klaar wanneer:** minstens drie extensies de afgesproken scenario’s op de relevante apparaatmatrix doorlopen. Een pakketdownload, catalogusvermelding of overgeslagen test telt niet als compatibiliteitsbewijs.

## 0.8 — Privacy, sessies en synchronisatie afronden

Deze functies bestaan al in 0.5.0. De ronde richt zich op aantoonbare betrouwbaarheid en het oplossen van gevonden fouten.

- Normale en private websitecookies testen bij tabwissel, geheugenopruiming, laatste privétab sluiten, crash en procesbeëindiging.
- Controleren dat private adressen/titels niet in geschiedenis of tabbackups terechtkomen. Bewust opgeslagen bestanden, bladwijzers en clipboard blijven expliciete blijvende acties.
- Site A kunnen wissen zonder site B uit te loggen; afzonderlijk wissen en camera-/microfoon-/locatierechten resetten op hardware controleren.
- Downloads met bestaande login, POST-antwoord, blob, compressie, onbekende lengte, offline raken en annuleren testen. Controleer gedeeltelijke bestanden, opnieuw aanvragen en openen.
- Twee clients tegen een echte eigen HTTPS/WebDAV-server gebruiken: nieuwe koppeling, toevoegingen, naamwijzigingen, verwijderingen, volgorde en ruimtegebrek.
- Verkeerde herstelzin, TLS-fout, timeout, gewijzigde ETag en gelijktijdige conflicten controleren. Geen stille lokale overschrijving of gedeeltelijke import.
- Lokale verbindingsgegevens vergeten, extern serverbestand verwijderen en herstel op een nieuw toestel documenteren/testen. Onafhankelijke review van het sync- en cryptografische ontwerp organiseren vóór een sterke beveiligingsclaim.

**Klaar wanneer:** het [privacybeleid](docs/privacy.md) en [syncprotocol](docs/sync.md) overeenkomen met werkelijk gemeten apparaat-/servergedrag. Er is geen gehoste accountdienst; eigen-serverbeheer blijft een expliciete afhankelijkheid.

## 0.9 — Beta voor dagelijks en handheldgebruik

- Android 9, Android 12+ met Material You en een recente Android-versie controleren; ARM64, oudere ARM waar hardware beschikbaar is, tablet en minstens één echte handheld meenemen.
- Koude/warme start, vaste pagina’s, tabwissel, geheugen met 1/5/12 tabs en batterijgedrag meten volgens de [benchmarkprocedure](docs/benchmark.md). Ruwe resultaten en mediaan/spreiding publiceren.
- De grens van 2–4 actieve paginasessies afstemmen op die metingen; reloads, media en niet-opgeslagen formulieren meenemen.
- Renderercrash, achtergrond/hervatten en geheugendruk testen zonder herhaalde crash-/reloadlus of verloren tabadressen.
- TalkBack, toetsenbord, D-pad en controller volledig door de hoofdflows laten gaan, met logische focus en zonder focusvallen.
- NL/EN, RTL, 200% tekst, rotatie, IME/systeembalken en verminderde animaties controleren.
- Veelgebruikte websites, webformulieren, fullscreen media en Android Autofill testen. Gevonden compatibiliteitsproblemen documenteren en prioriteren.
- Een langere testperiode met echte gebruikers houden; looptijd en aantal testers worden pas afgesproken wanneer toestellen en testcapaciteit beschikbaar zijn.

**Klaar wanneer:** de afgesproken matrix en hoofdscenario’s slagen, regressies verklaard/opgelost zijn en geen blokkerende problemen overblijven. Er is geen snelheidsclaim tegenover een andere browser zonder vergelijkbare metingen.

## 1.0 — Vrijgavecriteria

- [ ] Vaste ondertekening, onafhankelijke sleutelbackup en reproduceerbare veilige releaseprocedure.
- [ ] Echte installatie en update met aantoonbaar gegevensbehoud.
- [ ] Geen open blokkerende opstart-, navigatie-, privacy- of gegevensverliesproblemen.
- [ ] Browser-, instellingen-, bibliotheek-, download- en backupflows op de apparaatmatrix gecontroleerd.
- [ ] Privésessies en selectief wissen op hardware bewezen; geen bredere privacyclaim dan het bewijs ondersteunt.
- [ ] Ondersteunde extensies, versies en API-beperkingen gepubliceerd met echte testresultaten.
- [ ] Tweeclientsync en herstel tegen een echte server gecontroleerd; serverbeheer en beveiligingsgrenzen duidelijk.
- [ ] TalkBack, groot lettertype en handheld/controller meegenomen in acceptatie.
- [ ] Een geteste build voor dagelijks gebruik zonder debugmodus; optimalisatie/minificatie en updatepad opnieuw gecontroleerd.
- [ ] Privacyinformatie, changelog, licentievermeldingen, onderhoudsprocedure en bugmeldingen op orde. Een private route voor gevoelige beveiligingsmeldingen regelen.

## Eerste gebruikerstest: nu mogelijk

Gebruik de [ARM64-APK van 0.5.0](https://github.com/Tufein/Veyra/releases/download/v0.5.0/Veyra-0.5.0-arm64-v8a-debug.apk) voor een passende telefoon. Exporteer oude installatiegegevens vóór verwijderen; zie [migratie](docs/signing-and-migration.md).

| Volgorde | Scenario | Waarop letten |
| --- | --- | --- |
| 1 | Starten, zoeken/URL, terug/vooruit, drie tabs en herstart | Crashes, verloren adressen, onduidelijke bediening |
| 2 | Instellingen en beide thema’s; groter lettertype | Afkappen, contrast, terugnavigatie en opgeslagen voorkeuren |
| 3 | Website bewaren, vastzetten, hernoemen en losmaken | Gekoppelde namen, volgorde en behoud na herstart |
| 4 | Geschiedenis en één privétab | Scheiding van bezoeken en begrijpelijke privacykeuzes |
| 5 | Eén download opslaan, openen en annuleren | Android-bestandskiezer, voortgang en foutmeldingen |
| 6 | Eén extensie tegelijk installeren en beheren | Toestemming, popup en herstel bij een fout |

Noteer app-/Android-versie, toestel, stappen, verwacht gedrag en wat er gebeurt. Gebruik testgegevens en verwijder persoonlijke URLs, tokens, cookies en wachtwoorden uit bewijs. Voor uitgebreide acceptatie geldt de [apparaatmatrix](docs/device-validation.md).

## Bewust buiten deze reeks

Gehoste accounts, een eigen wachtwoordkluis en Play Store-publicatie vragen aparte product-/beheerbesluiten. Rechtstreekse passkeys voor willekeurige websites blijven afhankelijk van externe browser-/providergoedkeuring. Automatische achtergrondsync kan later worden onderzocht; de bestaande handmatige sync blijft onderdeel van de huidige roadmap. Een engine-/frameworkmigratie volgt alleen uit een concrete beperking en aantoonbaar voordeel.

## Opleveren per ronde

Elke ronde levert een begrensde verandering, passend automatisch bewijs, vermelde apparaatcontroles en een bijgewerkte roadmap/changelog. Bij appwijzigingen hoort steeds een APK met dezelfde vaste sleutel, correcte versiecode, checksums en gecontroleerde GitHub-downloadlinks. Alleen documentatie wijzigen maakt geen nieuwe appversie noodzakelijk. Een volgende ronde begint pas wanneer blokkerende fouten uit de huidige ronde zijn afgehandeld.
