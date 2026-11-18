# Verificatie — 1.0.0-rc.6

Releasekandidaat voor Android 9+, pakket `nl.veyra.browser`, versiecode 13, met dezelfde vaste ondertekeningssleutel als rc.3/rc.4/rc.5. Geen stabiele 1.0-claim: de [vrijgavecriteria](release-readiness.json) voor toestellen en beheer blijven open.

## Wijzigingen en regressiecontrole

- Adresbalkpositie instelbaar (`address_position`, standaard onderaan): de volgorde van de chrome-lagen in `BrowserActivity.buildChrome` wordt opgebouwd volgens de voorkeur; terugkeer uit de instellingen herbouwt de chrome.
- Tabs-snelkoppeling direct naast de adresbalk (nieuw icoon "tabs").
- Taboverzicht als kaartenraster van twee kolommen (`BrowserSheets.tabs`), met één kolom bij groot lettertype op smalle schermen; sluitknop, selectie, privátmarkering, limieten en heropenen zijn structuurbehoudend gebleven.
- Menu-actie "Gegevens wissen" opent direct het gegevensscherm (de bestaande selectieve wisfuncties zijn ongewijzigd).
- One UI-stijl grote titels op instellingenpagina's (`SettingsViews.heading`) en ruimere sectiekoppen.
- Nieuwe teksten in NL/EN; toegankelijkheidsrollen en content descriptions zijn behouden.

## Softwarecontroles

| Controle | Resultaat |
| --- | --- |
| Release-workflow (build, unit tests, Lint, beschermingstests, vaste sleutel, pin, checksums) | Zie [GitHub Actions](https://github.com/Tufein/Veyra/actions/workflows/release.yml) |
| Publicatie van de release met drie APK's en `SHA256SUMS.txt` | [Releasepagina](https://github.com/Tufein/Veyra/releases/tag/v1.0.0-rc.6) |

## Installeren

Rc.6 kan als update over rc.3/rc.4/rc.5 worden geïnstalleerd. Over 0.5.0/rc.1/rc.2 is dat niet mogelijk; exporteer gegevens eerst volgens de [migratiehandleiding](signing-and-migration.md).

De nieuwe bedieningselementen zijn nog niet op echte toestellen beoordeeld; neem de raster-tabbladen, adresbalkposities en instellingenkoppen op in de [apparaatmatrix](device-validation.md) voor 1.0.
