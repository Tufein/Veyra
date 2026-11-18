# Verificatie — 1.0.0-rc.5

Releasekandidaat voor Android 9+, pakket `nl.veyra.browser`, versiecode 12, met dezelfde vaste ondertekeningssleutel als rc.3/rc.4. Geen stabiele 1.0-claim: de [vrijgavecriteria](release-readiness.json) voor toestellen en beheer blijven open.

## Wijzigingen en regressiecontrole

- Volledige visuele vervanging: nieuw kleurensysteem (`styles.xml`), aangepaste chrome- en startpagina-opbouw (`BrowserActivity`), gedeelde instellingscomponenten (`SettingsViews`) en een nieuw app-icoon.
- Functionele structuur, teksten, content descriptions en toegankelijkheidsrollen zijn behouden; de wijziging is puur visueel.
- Certificaatpin blijft `606d529569d015a6ebc675d187cb5db7041a80177a8e46b24b3e19db42834594`.

## Softwarecontroles

| Controle | Resultaat |
| --- | --- |
| Release-workflow (build, unit tests, Lint, beschermingstests, vaste sleutel, pin, checksums) | Zie [GitHub Actions](https://github.com/Tufein/Veyra/actions/workflows/release.yml) |
| Publicatie van de release met drie APK's en `SHA256SUMS.txt` | [Releasepagina](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.5) |

## Installeren

Rc.5 kan als update over rc.4/rc.3 worden geïnstalleerd. Over 0.5.0/rc.1/rc.2 is dat niet mogelijk; exporteer gegevens eerst volgens de [migratiehandleiding](signing-and-migration.md).

De visuele wijziging is niet op echte toestellen beoordeeld. Kleurcontrast, grote tekst, RTL en kleine schermen moeten in de apparaatmatrix worden gecontroleerd voordat 1.0 wordt overwogen.
