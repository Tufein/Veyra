# Verificatie — 1.0.0-rc.4

Releasekandidaat voor Android 9+, pakket `nl.veyra.browser`, versiecode 11, met dezelfde nieuwe vaste ondertekeningssleutel als rc.3. Geen stabiele 1.0-claim: de [vrijgavecriteria](release-readiness.json) voor toestellen en beheer blijven open.

## Wijzigingen en regressiecontrole

- Sync-afhandeling is robuuster: ontbrekende lokale gegevens worden als lege backup behandeld (`SyncMerger`), een onleesbare merge-baseline wordt genegeerd in plaats van de samenvoeging te laten falen, en lege/incomplete servergegevens (`SyncClient`) geven een duidelijke foutmelding in plaats van een crash.
- De wijziging is opgenomen in commit `2e2d115` en zit daarmee voor het eerst in een ondertekende release; rc.3 bevatte deze code nog niet.
- Certificaatpin blijft `606d529569d015a6ebc675d187cb5db7041a80177a8e46b24b3e19db42834594` in [signing-certificate-sha256.txt](signing-certificate-sha256.txt).

## Softwarecontroles

| Controle | Resultaat |
| --- | --- |
| Release-workflow (build, unit tests, Lint, beschermingstests, vaste sleutel, pin, checks) | [Geslaagd](https://github.com/Tufein/Veyra/actions/runs/44284173707) |
| Publicatie van de release met drie APK's en `SHA256SUMS.txt` | [Geslaagd](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.4) |

Er zijn geen assertions verwijderd of tests overgeslagen.

## Installeren

Rc.4 kan als update over rc.3 worden geïnstalleerd (gelijke sleutel). Over 0.5.0/rc.1/rc.2 is dat niet mogelijk; exporteer gegevens eerst volgens de [migratiehandleiding](signing-and-migration.md). Controleer certificaat en `SHA256SUMS.txt` voor installatie.

De nog open toestelcriteria (native Gecko-start, fullscreen, private cookies, echte update, tweetoestelsync, toegankelijkheid) zijn ongewijzigd en niet met deze release aangetoond.
