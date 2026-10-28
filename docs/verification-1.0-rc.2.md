# Verificatie — 1.0.0-rc.2

Releasekandidaat voor Android 9+, pakket `nl.veyra.browser`, versiecode 9, dezelfde vaste ondertekeningssleutel als 0.5.0 en rc.1. Geen stabiele 1.0-claim: de [vrijgavecriteria](release-readiness.json) voor toestellen en beheer blijven open.

## Wijzigingen en regressiecontrole

- De huidige privétab toont een blijvende InPrivate-balk, privétabaantal en eigen adresbalkkleuren. Deze balk staat buiten de bij fullscreen verborgen browserknoppen. De verbindingsindicator blijft HTTP/HTTPS afzonderlijk tonen.
- Privé-startpagina en tabkaarten tonen een masker en duidelijke tekst; de privé-startpagina leest geen persoonlijke begroeting of snelkoppelingen. Normaal/privé aanmaken heeft aparte knoppen.
- Dupliceren opent hetzelfde privacytype. Heropenen neemt alleen geldige normale webadressen op, maximaal vijf, uitsluitend in RAM. Startpagina’s en privétabs worden uitgesloten. Geschiedenis verwijderen maakt alle levende heropenlijsten ongeldig; gegevens wissen leegt de lijst ook.
- Alle privétabs sluiten vraagt bevestiging en behoudt normale tabs. Selectie en sluiten vanuit een open taboverzicht koppelen terug naar het oorspronkelijke tabobject om verouderde posities te vermijden.
- Browserdialogen nemen FLAG_SECURE over; taboverzichten met private inhoud beveiligen hun eigen venster ook als de huidige pagina normaal is. Bewust gekopieerde privélinks krijgen op Android 13+ de gevoelige-klembordmarkering.
- Native-widgettests controleren labels, aantallen, onafhankelijke acties, private venstervlaggen, beide nieuwe-tabknoppen bij de tablimiet en bereikbaarheid na scrollen op 320dp met 200% tekst.

## Softwarecontroles

| Controle | Resultaat |
| --- | --- |
| Debug-hosttests | 127 geslaagd; 0 fouten, failures of skips |
| Release-hosttests | 127 geslaagd; 0 fouten, failures of skips |
| Geïsoleerde echte lokale HTTPS-integratietests | 5 geslaagd |
| Node-beschermingstests | 8 geslaagd |
| Python-hulpmiddeltests | 23 geslaagd |
| Android Lint | 0 bevindingen voor debug en release na opschoning van twee oude labels en een teltekst |
| Releasebuild | R8, resource shrinking, bestaande vaste sleutel; drie ABI-APK’s gebouwd |
| Native ELF LOAD-uitlijning | 13 libraries per ARM64- en x86_64-APK voldoen aan 16KiB |
| Instrumentatietest-APK | Gebouwd; geen uitvoering op toestel |
| GitHub Android CI | [Geslaagd voor fb1eb8f](https://github.com/Tufein/Veyra/actions/runs/40727837579) |

De volledige lokale suite draaide op de appcode van `bd7b963`. Daarna zijn uitsluitend twee ongebruikte resources verwijderd en de aantallentekst verduidelijkt (`fb1eb8f`); releasebuild/Lint zijn opnieuw lokaal geslaagd en de volledige GitHub CI is op die definitieve appversie geslaagd. Er zijn geen assertions verwijderd of tests overgeslagen. De nieuwe scrolltest controleert de eindpositie zonder animatietiming.

[Privé-start donker](images/rc2-private-home-dark.png), [licht](images/rc2-private-home-light.png) en [taboverzicht](images/rc2-tabs-private-dark.png) zijn native Android-widgetrenderingen onder Robolectric, geen telefoonscreenshots. De aantallentekst in het taboverzicht is daarna verduidelijkt naar ‘Normaal: … · InPrivate: …’; de vormgeving blijft gelijk.

## Gepubliceerde APK’s

De [release](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.2) is publiek. [GitHub CI op de tagcode](https://github.com/Tufein/Veyra/actions/runs/40776467266) en de [publicatiecontrole](https://github.com/Tufein/Veyra/actions/runs/40777082483) zijn geslaagd. Alle drie APK’s en SHA256SUMS.txt geven HTTP200; GitHubs assetdigests komen overeen met de lokaal gecontroleerde bestanden. ARM64-SHA256: `803f7843fdc398e40c1cf67045401f3eefd2de0db9d3908a635e8a16ada60f3b`.

De tag wijst naar `f8547d9`; de appcode is die van `fb1eb8f`, met daarna alleen documentatie en vernieuwde VCS-buildmetadata. Publicatie controleert certificaat, pakket, versie/code, ABI, niet-debuggable/test-only, zipalign en checksums. De sleutel is niet overgedragen. Een eerste uploadpoging stopte zonder assets; een nieuwe poging met dezelfde gecontroleerde APK’s is geslaagd. Een foutannotatie is aan het tijdelijke publicatiescript toegevoegd om volgende fouten via de check-API te kunnen lezen. De tijdelijke artifactbranch is verwijderd.

## Toesteltest

Er is geen Android-toestel verbonden. De widgetafbeeldingen worden onder Robolectric gemaakt; zij bewijzen geen native Gecko-start, fullscreen video, screenshots/recents op een telefoon, private cookie-isolatie, Android-klembordgedrag of behoud van gegevens bij installatie. Die scenario’s staan in de [apparaatmatrix](device-validation.md#extra-acceptatie-voor-100-rc2-nog-uit-te-voeren).

Installeer rc.2 als update over rc.1 of 0.5.0 zonder verwijderen. Test normale en private tabs naast elkaar, dupliceren, normaal heropenen en bevestigd privé sluiten. Privétabs mogen nooit in geschiedenis/tabherstel/heropenen terechtkomen; de netwerkverbinding wordt door InPrivate niet anoniem.
