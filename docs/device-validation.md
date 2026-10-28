# Apparaatvalidatie voor 0.5 en de 1.0-kandidaat

Status op 27 oktober 2026: **niet uitgevoerd op fysieke toestellen**. De lokale software-emulator leverde geen bruikbaar testresultaat; Robolectric-renderingen zijn geen hardwarebewijs. Deze matrix blijft een vrijgavepoort voor beta/1.0.

| Doel | Installatie/update | Browser/downloads | Privé/rechten | Extensies | Bediening |
| --- | --- | --- | --- | --- | --- |
| Android 9, ARM waar beschikbaar | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd |
| Android 12+, ARM64 met Material You | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd |
| Recente Android-versie, ARM64 | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd |
| Echte handheld met controller | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd |
| Tablet/landscape/venstermodus | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd | Niet uitgevoerd |

## Geautomatiseerd op een geïsoleerd testtoestel

Bouw ook `:app:assembleDebugAndroidTest` met dezelfde vaste sleutel als de app. Gebruik geen telefoon met waardevolle browsergegevens voor instrumentatietests: deze openen en veranderen testinstellingen. De runner verwijdert geen app en stopt bij een andere geïnstalleerde handtekening.

```sh
python tools/prepare_addon_fixtures.py --output /private/path/amo-fixtures
python tools/device_acceptance.py --serial DEVICE   --apk app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk   --apksigner /path/to/build-tools/37.0.0/apksigner   --addon-dir /private/path/amo-fixtures   --output /private/path/device-results.json --confirm-isolated-test-device
```

De fixturetool controleert officiële Mozilla-HTTPS-pakketten en AMO-checksums voor uBlock Origin, Dark Reader en ClearURLs. Downloaden bewijst geen compatibiliteit. De runner legt werkelijke resultaten en overgeslagen tests vast. Een bestaande extensie-installatie wordt behouden; een overgeslagen fixture telt niet als geslaagd. De optionele installatietest omvat toestemmingsvenster, aan/uit, activiteitrecreatie en verwijderen; popups, echte updates en dagelijks sitegedrag vragen aanvullende controles.

De runner controleert certificaten en, waar `run-as` beschikbaar is, behoud van voorkeurbytes direct bij een bestaande update. Controleer na starten ook de betekenis van instellingen, bladwijzers, tabs en extensies. Een schone installatie is geen updatebewijs.

De HTTP-browsersmoketest gebruikt een eigen fixture en maakt tabs/bladwijzers. Stel de app op Nederlands, zet blokkering aan en kies HTTPS heeft voorkeur:

```sh
python tools/smoke_test.py --serial DEVICE --confirm-isolated-test-device
```

## Handmatige acceptatie

1. **Installatie en migratie:** oude alpha exporteren vóór verwijderen; JSON controleren; nieuwe app installeren/importeren; volgende APK met dezelfde vaste sleutel via `adb install -r` bijwerken. Herstarten en alle bewaarde gegevens controleren.
2. **Navigatie:** typed zoeken/URL, HTTPS/HTTP/redirects, certificaatfout, JS, terug/vooruit, 12 tabs, externe links, target blank, bestand uploaden, zoeken op pagina en aangepast zoektemplate. Maak één formulier met niet-opgeslagen invoer en leg herladen bij sessie-evictie vast.
3. **Downloads:** ingelogde HTTP(S)-website en POST-antwoord, blob, onbekende lengte/compressie, wifi uit, annuleren, SAF-weigering, procesbeëindiging, gedeeltelijk bestand, opnieuw aanvragen zonder POST-herhaling en bestand openen. Vergelijk SHA-256 met de bron.
4. **Privé:** normale en private logins scheiden, tabwissel/evictie zonder verlies van open private cookies, laatste privétab sluiten, app/proces beëindigen, bestandsscan van prefs/profiel en opnieuw openen. Controleer geen private adressen in geschiedenis/tabbackup, geen private Autofill, geen gebruikersuitbreidingen, alleen vluchtige adblock-uitzonderingen.
5. **Rechten en opslag:** camera/microfoon/locatie weigeren/toestaan; Android approximate locatie; HTTPS-origin/poort/iframe; tabwissel tijdens dialoog; blokkade terugdraaien; opname stoppen; cookieaantallen zonder waarden; site A wissen zonder site B uit te loggen; afzonderlijke wisopties en annuleren.
6. **Extensies:** test alle drie kandidaten op eigen websites: ondertekend installeren/toestemming weigeren, browseractie/popup/opties, tabs create/activeren/bijwerken/sluiten, herstart, echte update met extra rechten weigeren/toestaan, ongetekend pakket weigeren, foutieve uitbreiding uitschakelen/herstelmodus. Vermeld API-beperkingen per pakket.
7. **Sync:** twee toestellen en een echte beheerste HTTPS/WebDAV-server; nieuwe koppeling, verkeerde herstelzin, offline/timeout, TLS-fout, redirects, gewijzigd server-ETag, gelijktijdige naamwijziging/verwijdering/volgorde en ruimtegebrek. Controleer serverbestand geen plaintext URLs; wissen/vergeten en herstel op nieuw toestel.
8. **Toegankelijkheid/media:** TalkBack, logische knoprollen, 48dp, 200% tekst, 320dp, RTL, donkere/lichte/wallpaperkleuren, rotatie, IME/insets, controller/D-pad/toetsenbord zonder focusval en verminderde animaties; fullscreen video en terugnavigatie.
9. **Robuustheid/prestaties:** renderercrash/kill, achtergrond/hervatten en geheugendruk zonder reloadlus; gebruik de [benchmarkprocedure](benchmark.md), registreer ruwe resultaten en herhaal op dezelfde hardware.

Schrijf per resultaat toestel, Android/ABI, appversie/certificaat, Gecko-versie, extensieversies, stappen en bewijs op. Rapporteer een mislukking of ontbrekend apparaat als zodanig; vul de matrix pas na echte uitvoering in.

## Geoptimaliseerde 1.0-kandidaat

De debug-instrumentatiesuite en de geoptimaliseerde release-APK hebben verschillende doelen. Voer de bestaande debug-app/test-APK-combinatie op een geïsoleerd toestel uit. Controleer daarnaast de daadwerkelijk gepubliceerde niet-debuggable release-APK handmatig: starten, browsen, webformulieren, media, permissies, private cookies en een echte update vanaf 0.5.0. Hosttests voor de releaseconfiguratie vervangen die proef niet.

De runner meldt `installation_mode` en `update_verification`. Een schone installatie is `not_applicable` voor updatebewijs. Als Android na een update `run-as` weigert wegens de niet-debuggable doelbuild, blijft het voorkeurenbewijs `unverified_target_not_debuggable`; dit is geen bewijs van verlies of behoud. Controleer gegevens daarna in de app en voeg echte resultaten toe aan het vrijgaveverslag.
