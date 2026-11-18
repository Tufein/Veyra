# Verificatie — 1.0.0-rc.3

Releasekandidaat voor Android 9+, pakket `nl.veyra.browser`, versiecode 10, met een **nieuwe vaste ondertekeningssleutel**. De eerdere sleutel van 0.5.0/rc.1/rc.2 is niet meer beschikbaar; rc.3 is daarom geen update over die versies. Geen stabiele 1.0-claim: de [vrijgavecriteria](release-readiness.json) voor toestellen en beheer blijven open.

## Wijzigingen en regressiecontrole

- Sync-afhandeling is robuuster: ontbrekende lokale gegevens worden als lege backup behandeld, een onleesbare merge-baseline wordt genegeerd in plaats van de samenvoeging te laten falen, en lege/incomplete servergegevens geven een duidelijke foutmelding in plaats van een crash.
- De ondertekeningssleutel is opnieuw vastgezet. Het certificaat is gepind in [signing-certificate-sha256.txt](signing-certificate-sha256.txt): `606d529569d015a6ebc675d187cb5db7041a80177a8e46b24b3e19db42834594`.
- Rc.2-functionaliteit (blijvende InPrivate-balk, privé-startpagina, privacybewust dupliceren, normaal heropenen, webadres kopiëren, bevestigd privé sluiten) blijft aanwezig.

## Softwarecontroles

| Controle | Resultaat |
| --- | --- |
| Android CI op de tagcode | [Geslaagd](https://github.com/Tufein/Veyra/actions/runs/44180854064) |
| Release-workflow (build, unit tests, Lint, beschermingstests) | [Geslaagd](https://github.com/Tufein/Veyra/actions/runs/44180854017) |
| Vaste sleutel uit GitHub Secrets geladen en certificaatpin geverifieerd | Geslaagd |
| APK-versie/ABI/certificaat/niet-debuggable/uitlijning | Geslaagd |
| Publicatie van de release met drie APK's en `SHA256SUMS.txt` | [Geslaagd](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.3) |

Eerdere pogingen in dezelfde ronde stopten bewust bij ontbrekende secrets en een versie-tag-mismatch; die zijn gecorrigeerd voordat de uiteindelijke tagcode is gebouwd en gepubliceerd. Er zijn geen assertions verwijderd of tests overgeslagen.

## Gepubliceerde APK's en onafhankelijke controle

De [release](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.3) bevat `Veyra-1.0.0-rc.3-arm64-v8a.apk`, `-armeabi-v7a.apk`, `-x86_64.apk` en `SHA256SUMS.txt`. De gedownloade ARM64-APK is lokaal gecontroleerd:

- SHA-256 komt overeen met de `SHA256SUMS.txt` van de release.
- `apksigner --print-certs` toont certificaat-DN `CN=Veyra Release, OU=Veyra, O=Tufein, C=NL` en SHA-256 `606d529569d015a6ebc675d187cb5db7041a80177a8e46b24b3e19db42834594`, gelijk aan de pin.

## Toesteltest

Er is geen Android-toestel verbonden. De eerder genoemde grenzen blijven gelden: native Gecko-start, fullscreen video, private cookie-isolatie, Android-klembordgedrag en behoud van gegevens bij installatie zijn niet aangetoond. Installeer rc.3 als **nieuwe installatie** (niet als update over rc.1/rc.2/0.5.0); exporteer oude gegevens eerst volgens de [migratiehandleiding](signing-and-migration.md).
