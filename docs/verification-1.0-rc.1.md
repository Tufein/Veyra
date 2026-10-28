# Verificatie — 1.0.0-rc.1

**Dit is een releasekandidaat voor 1.0, geen afgeronde stabiele lancering.** Pakket `nl.veyra.browser`, versiecode 8, minimum Android 9/API28, target35. De bestaande vaste sleutel uit 0.5.0 blijft behouden. Alle open toestel- en beheercriteria staan in [release-readiness.json](release-readiness.json).

## Uitgevoerde softwarecontroles

| Controle | Werkelijk resultaat |
| --- | --- |
| Debug hosttests | 114 geslaagd; 0 fouten, failures of skips |
| Release hosttests | 114 geslaagd; 0 fouten, failures of skips |
| Aparte HTTPS-integratiesuite | 5 geslaagd, met echte lokale TLS/HTTP-verzoeken tussen onafhankelijke JVM-clients |
| Bescherming, Node | 8 geslaagd |
| Python hulpmiddelen | 22 geslaagd, inclusief migratie, certificaatparser, publicatiecontrole, releasecriteria en gemockte ADB-besturing |
| Android Lint | 0 bevindingen voor debug én release |
| Releasebuild | R8/minificatie en resource shrinking bouwen succesvol; publieke APK’s zijn niet debuggable/test-only |
| Pakketcontrole | Werkelijke APK-pakketnaam, versie/code en native ABI vergeleken met metadata |
| Ondertekening | Alle drie release-APK’s voldoen aan de vaste SHA-256-certificaatpin |
| Uitlijning | APK’s gecontroleerd met zipalign voor 16KiB; 13 native libraries per ARM64- en x86_64-APK voldoen aan 16KiB ELF-loadalignment |
| Instrumentatietest-APK | Gebouwd; niet uitgevoerd op Android |
| Fysieke toestellen | Geen toestel aangesloten volgens `adb devices -l`; geen fysieke resultaten geclaimd |

De twee hostvarianten leveren 228 testuitvoeringen, plus 5 HTTPS-integratietests. Dat zijn geen 233 verschillende productfuncties en geen toesteltests. De Python-tests voor de ADB-runner gebruiken mocks en bewijzen geen werkelijke installatie of update.

## Wat is aangescherpt?

- Normale tabadressen worden bij backupimport alleen met expliciete keuze hersteld. Bestaande klaargezette adressen worden behouden; een te volle combinatie stopt vóór wijzigingen.
- Een hostreset trekt Veyra-toestemmingen voor alle poorten op die host in en laat andere hosts ongemoeid.
- Sync weigert ongeldige/zwakke/wildcard-ETags, zodat ongeldige servermetadata geen wildcard-overschrijving veroorzaakt. HTTP202 telt niet als bevestigde opslag.
- De downloadkopie heeft regressietests voor bytebehoud, afgebroken antwoorden, annuleren, volle opslag en onbekende lengte.
- Privacy-/sync-/toestemmingskeuzes gebruiken Material-checkboxes. Websitevoorkeuren gebruikt de juiste NL/EN-titel, bewaart de gekozen website bij rotatie en laadt grote lijsten in delen.
- De apparaatrunner kan een schone installatie voorbereiden en meldt ontoegankelijke voorkeuren na overstap naar een niet-debuggable build als **onbewezen**, niet als behouden of verloren.

## Betekenis en grenzen van de tests

De echte HTTPS-fixture gebruikt een tijdelijk eigen certificaat met correcte localhost-hostnaam en een uitsluitend voor de fixture vertrouwde CA. Productie-TLS-verificatie blijft aan. Tests controleren twee clients, versieconflicten, gelijktijdige aanmaak, geweigerde redirects, onbetrouwbare certificaten en verkeerde hostnamen. Dit vervangt geen twee Android-telefoons met een beheerde WebDAV-server.

Robolectric verandert globale security providers. Daarom draait de HTTPS-suite in een aparte test-JVM via `:app:syncHttpsIntegrationTest`; `:app:check` en beide CI-routes nemen deze suite mee. Er zijn geen assertions verwijderd of tests als geslaagd overgeslagen. Release-hosttests controleren de releaseconfiguratie; zij bewijzen geen native Gecko-start uit een R8-APK op een telefoon.

De nieuwe afbeeldingen zijn [privacy](images/rc1-privacy-english-dark.png) en [sync](images/rc1-sync-english-dark.png). Het zijn native Android-widget-renderingen onder Robolectric, geen telefoonscreenshots.

## Publicatie en open criteria

De kandidaat wordt gepubliceerd via `publish-prebuilt.yml` met de bestaande vaste-key-APK’s. De tijdelijke branch bevat uitsluitend gecomprimeerde publieke APK’s, metadata, checksums en controlegereedschap. De workflow verifieert ondertekening, pakket/versie/ABI, niet-debuggable status en oorspronkelijke checksums vóór upload; bestaande afwijkende assets worden nooit overschreven. De privésleutel wordt niet overgedragen.

`tools/release_readiness.py` laat een eerlijk gelabelde kandidaat toe en blokkeert een stabiele 1.x-publicatie zolang verplichte criteria onbewezen zijn. Het controleert de aanwezigheid/structuur van beoordeelde bewijskoppelingen; een mens moet de inhoud en geldigheid van dat bewijs beoordelen.

Nog open: onafhankelijke sleutelbackup en signing-secrets voor automatische rebuilds, echte updates, native release-smoketest, apparaatmatrix, privacy/wissen op hardware, drie extensies, twee Android-syncclients, TalkBack/controller, benchmarks en een private beveiligingsmeldroute. Secrets-beheer en inschakelen van private vulnerability reporting worden door de huidige GitHub-integratie met HTTP403 geweigerd. De oorspronkelijke directe uploadroute uit de cloud geeft HTTP401; publicatie via repository Actions heeft bij 0.5.0 wel gewerkt.

De officiële toestelmatrix blijft [device-validation.md](device-validation.md). Alleen een groene build of een veranderde versienaam sluit deze criteria niet af.
