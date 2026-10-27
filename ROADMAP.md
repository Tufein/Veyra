# Veyra — productroadmap

Bijgewerkt: 27 oktober 2026. Dit is de ontwikkelvolgorde, geen kalenderbelofte. De 0.4.0-alpha is een testrelease met openstaande apparaat- en updatecontroles; zie de [releasebeperkingen](docs/releases/v0.4.0.md). **Geïmplementeerd**, **automatisch getest** en **op een apparaat geverifieerd** zijn verschillende statussen.

Veyra wordt een rustige, persoonlijke Android-browser voor telefoons en handhelds. De prioriteit is betrouwbare dagelijkse navigatie met een verzorgde Material You-interface, bruikbare Firefox-extensies en duidelijke privacykeuzes. Meer functies mogen de browser niet onoverzichtelijk of onnodig zwaar maken.

## Waar we nu staan

| Onderdeel | Huidige status | Nog te bewijzen |
| --- | --- | --- |
| 0.3.0 | Eerste openbare alpha met APK’s op GitHub | Gebruik op echte toestellen; stabiele updatehandtekening |
| Browsen met GeckoView | Geïmplementeerd: navigatie, tabbladen, herstel van adressen, zoeken op pagina | Websitecompatibiliteit, rendererherstel en langdurig dagelijks gebruik |
| Lokale advertentieblokkering | Gebundelde regels, cosmetische filtering, uitzonderingen en HTTPS-beleid; regressietests aanwezig | Betrouwbaarheid op echte websites en wisselwerking met extensies |
| Firefox-extensies | Catalogus, ondertekende installatie, toestemmingsvensters en beheer aanwezig | Installatie, popups en ondersteunde API’s per extensie op een apparaat |
| Material You-instellingen 0.4.0 | Nieuwe instellingenhub, detailpagina’s, zoeken, eigen editor en uitgebreidere kleurrollen beschikbaar als testalpha | Visuele en functionele controle op een telefoon naast de UI-tests |
| Bibliotheek op main na 0.4.0 | Bladwijzers en startpagina samen beheren, vastzetten/losmaken, gekoppeld bewerken en bevestigd verwijderen | Gebruik op hardware; import/export en bredere dagelijkse browserfuncties |
| Startpagina | Begroeting, compact beeld, maximaal acht eigen snelkoppelingen | Groot lettertype, kleine schermen, toetsenbord en controller op hardware |
| Downloads, browsegeschiedenis, privétabs, synchronisatie | Niet geïmplementeerd | Volledige onderstaande oplevercriteria |

## Werkvolgorde

| Mijlpaal | Doel | Prioriteit | Afhankelijk van |
| --- | --- | --- | --- |
| 0.4.0 — Een eigen Veyra | Afzonderlijke instellingen en een samenhangende Material You-interface | P0, huidige ontwikkelronde | UI-tests en apparaatcontrole |
| 0.4.1 — Een betrouwbare basis | Reproduceerbare updates, apparaatvalidatie en crashherstel | P0 | Ondertekeningssleutel en testtoestellen |
| 0.5 — Dagelijks bruikbaar | Downloads, geschiedenis, bladwijzerbeheer en websitevoorkeuren | P1 | Basisvalidatie en veilige gegevensopslag |
| 0.6 — Extensies die je kunt vertrouwen | Compatibiliteitsmatrix, betere toestemmingen en herstelbare updates | P1 | Gecko-API-validatie op hardware |
| 0.7 — Snel en prettig op handhelds | Metingen, tabgeheugen, adaptieve bediening en controllerfocus | P1 | Herhaalbare benchmarks en stabiele sessies |
| 0.8 — Privacy onder jouw controle | Privétabs, selectief gegevens wissen en duidelijke sitegegevens | P1 | Aantoonbare scheiding van sessiegegevens |
| 0.9 — Beta | Toegankelijkheid, migraties, foutmeldingen en een brede testmatrix | P1 | Alle dagelijkse basisfuncties gevalideerd |
| 1.0 — Eerste stabiele versie | Betrouwbare installatie, updates en dagelijks gebruik | Vrijgavepoort | Alle criteria onder ‘Wanneer is 1.0 klaar?’ |
| Na 1.0 | Eventuele versleutelde synchronisatie en verdere uitbreidingen | P2 | Een expliciet privacy- en productbesluit |

## 0.4.0 — Een eigen Veyra

### Deze ontwikkelronde

- [x] Eén apart instellingenscherm met een herkenbare terugknop en zonder browserwerkbalk.
- [x] Een overzicht met eigen pagina’s voor uitstraling, startpagina, lezen/zoeken, privacy, snelkoppelingen, wachtwoordmanagers en versie-informatie.
- [x] Zoeken op instellingen en synoniemen, inclusief een duidelijke toestand zonder zoekresultaten.
- [x] Een snelkoppelingeneditor binnen de instellingen, inclusief toevoegen, bewerken, verwijderen, URL-controle en de bestaande limiet van acht websites.
- [x] Behoud van geopende instellingenpagina, navigatiegeschiedenis en niet-opgeslagen formulierinvoer bij schermrotatie.
- [x] Een live kleurvoorbeeld, lichte/donkere/systeemstand, achtergrondkleuren en vier herkenbare accenten.
- [x] Samenhangende oppervlakken, contrastkleuren, typografie, afstand en aanraakgebieden.
- [x] Aanpasbare themakeuze voor kleine schermen en grote lettertypen.
- [x] De startpagina gebruikt dezelfde gekozen Material-kleuren.
- [x] Bestaande voorkeuren, bladwijzers en tabadressen behouden.
- [ ] Visuele goedkeuring op een echt Android-toestel, in beide thema’s en met een groot lettertype.

### Daarna binnen dezelfde ontwerprichting

- [x] Een rustig Material 3-bodemmenu met de belangrijkste browseracties binnen duimbereik (doorontwikkeling op main na 0.4.0; apparaatcontrole open).
- [x] Een tabbladenpaneel met duidelijke selectie en aparte sluitknoppen (doorontwikkeling op main na 0.4.0; apparaatcontrole open).
- Consistente pictogrammen en benoeming van alle acties; geen instellingsdialogen die onverwacht naar een ander onderdeel terugsturen.
- Op tablets een bredere of tweedelige instellingenindeling, zonder uitgerekte telefoonkaarten.

**Klaar wanneer:** alle instellingen via hun eigen pagina bereikbaar zijn, Terug de vorige pagina herstelt, opgeslagen voorkeuren na opnieuw openen blijven gelden en de UI leesbaar blijft op een scherm van 320 dp met groot lettertype. Een UI-rendering vervangt de controle op een echt toestel niet.

## 0.4.1 — Eerst betrouwbaarheid en updates

Dit werk heeft voorrang boven extra browserfuncties.

1. **Vaste ondertekening.** Kies en beveilig de toekomstige ondertekeningssleutel, sla die uitsluitend in de daarvoor bestemde geheime opslag op en gebruik dezelfde sleutel voor volgende builds. Leg vast welke bestaande alpha-installaties in-place kunnen worden bijgewerkt. Geen geheime sleutels in Git of releasebestanden.
2. **Apparaatmatrix.** Test minimaal Android 9, Android 12+ met Material You en een recente Android-versie; voeg minimaal één echte handheld toe. Test ARM64 en de ondersteunde oudere ARM-variant waar hardware beschikbaar is.
3. **Browsersmoketest.** Controleer HTTPS/HTTP-beleid, redirects, JavaScript, terug/vooruit, tabbladen, adresherstel, zoeken op pagina, bestandskiezer en bladwijzers.
4. **Sessiestabiliteit.** Controleer renderercrashes, geheugendruk, hervatten na achtergrondgebruik en schermrotatie. Herstel mag geen eindeloze foutlus veroorzaken of oude tabadressen verliezen.
5. **Extensiesmoketest.** Installeer een ondertekend pakket met expliciete toestemming, controleer weigeren, uitschakelen, verwijderen en opnieuw starten van de app. Een ongetekend pakket moet worden geweigerd.
6. **APK en updates.** Verifieer versie, pakketnaam, ABI en handtekening; test een echte update met behoud van instellingen, bladwijzers en tabadressen. Publiceer duidelijke downloadnamen en checksums.

**Klaar wanneer:** installatie én update op de testmatrix zijn uitgevoerd, blokkerende crashes zijn opgelost en het verificatieverslag werkelijk uitgevoerde controles met resultaten vermeldt. Geen stabiele claim op basis van alleen een geslaagde build.

## 0.5 — Dagelijkse browserfuncties

| Werkpakket | Gewenst gedrag | Oplevercriterium |
| --- | --- | --- |
| Downloads | Voortgang, annuleren, opnieuw proberen en bestand openen | Werkt met onderbroken netwerk en geldige websitesessies; veilige bestandsnamen en Android-opslag; foutmeldingen zonder stil gegevensverlies |
| Browsegeschiedenis | Lokale geschiedenis doorzoeken en selectief verwijderen | Geen opslag van privésessies; duidelijke domeinen, titels en tijdstippen; privacybeleid klopt met de opslag |
| Bladwijzerbeheer | Zoeken, bewerken, ordenen en exporteren/importeren | Ronde export/import behoudt geldige gegevens; ongeldige invoer veroorzaakt geen overschrijven van bestaande bladwijzers |
| Websitevoorkeuren | Per-site toestemmingen en begrijpelijke reset | Camera, microfoon en locatie pas na een expliciete keuze; weigeren blijft bruikbaar en is later terug te draaien |
| Standaardbrowser | Veyra instellen via de Android-browserrol | Werkt op ondersteunde Android-versies en keert correct terug uit systeeminstellingen |
| Zoekvoorkeuren | Eigen zoekaanbieder met controle van de zoek-URL | Een ongeldige sjabloon wordt geweigerd; bestaande aanbieders blijven werken |
| Tabherstel | Herstel na normaal afsluiten en procesbeëindiging | Geen onbedoelde dubbele tabbladen; duidelijk wat wel en niet van een webformulier wordt bewaard |

**Afgrenzing:** downloadbeheer en site-toestemmingen mogen certificaatcontrole, sandboxing of Android-opslagbeperkingen niet verzwakken.

## 0.6 — Volwaardige extensie-ervaring

- Een zichtbare compatibiliteitsmatrix voor bijvoorbeeld uBlock Origin, Dark Reader en ClearURLs. De lijst beschrijft uitgevoerde tests; vermelding is geen voorafgaande garantie.
- In de catalogus geschikte Android-extensies herkennen, zoeken, installatiestatus tonen en mislukte downloads opnieuw kunnen proberen.
- Op de detailpagina versie, bron, vereiste rechten, sitebereik en eventuele gegevensverzameling begrijpelijk tonen.
- Browseractieknoppen, popups en opties logisch koppelen aan het actieve tabblad.
- `tabs.create`, tabactivatie, bijwerken en sluiten controleren tegen echte extensies; unsupported API’s documenteren.
- Nieuwe rechten bij een update zichtbaar vergelijken en pas na toestemming toepassen.
- Een extensie kunnen uitschakelen wanneer een website of opstartprocedure niet meer goed werkt, zonder alle browsergegevens te wissen.
- Herstart, profielherstel en de wisselwerking tussen ingebouwde blokkering en een extra adblocker testen.
- Native messaging uitsluitend aanbieden aan de eigen ingebouwde beschermingsmodule; geen algemene bridge naar websites of gedownloade extensies.

**Klaar wanneer:** minstens drie relevante extensies de afgesproken scenario’s op de testmatrix doorlopen, toestemming en weigeren getest zijn en een fout in één extensie herstelbaar is.

## 0.7 — Prestaties en handheldbediening

### Eerst meten

Gebruik dezelfde toestellen, websites, netwerkcondities en engineversies voor vergelijkingen. Meet koude en warme start, paginaladen, tabwissel, geheugengebruik met 1/5/12 tabbladen en batterijgedrag tijdens een vaste taak. Rapporteer mediaan en uitschieters. Maak geen snelheidsclaim tegenover een andere browser zonder metingen op hetzelfde toestel.

### Daarna verbeteren

- De grens voor actieve sessies afstemmen op gemeten geheugenverbruik; inactieve tabbladen gecontroleerd opruimen en herstellen.
- Grote formulieren en mediapagina’s testen voordat sessies agressiever worden vrijgegeven.
- Een bruikbare toetsenbord-, D-pad- en controllerfocus: zichtbare selectie, logische volgorde en geen focusvallen.
- Schaalbare bediening op kleine handheldschermen, landscape, tablets en venstermodus.
- Terug/vooruit, adresinvoer en tabwissel verbeteren voor bediening met één hand.
- Geanimeerde overgangen beperken wanneer Android animaties uitschakelt.

**Klaar wanneer:** de benchmark herhaalbaar is, regressies verklaard zijn en de belangrijkste browse- en instellingenflows met controller én aanraking te bedienen zijn.

## 0.8 — Privacy en gegevensbeheer

1. **Privétabs:** aparte privésessies en aantoonbaar geen blijvende opslag van hun adressen, geschiedenis of websitegegevens. Controleer ook procesbeëindiging, tabherstel en extensies.
2. **Gegevens wissen:** afzonderlijke keuzes voor geschiedenis, cookies/sitegegevens, cache en lokale voorkeuren. Laat zien welke accounts mogelijk worden afgemeld; wis pas na de gekozen bevestiging.
3. **Sitegegevens:** per website inzicht in opgeslagen gegevens en een gerichte reset, zonder gegevens van andere websites te raken.
4. **Toestemmingen:** duidelijk overzicht van verleende rechten en makkelijk intrekken.
5. **Gegevens meenemen:** lokale export/import van bladwijzers en ondersteunde voorkeuren, met versiecontrole en bescherming tegen gedeeltelijke of ongeldige imports.

**Klaar wanneer:** de beloofde scheiding en verwijdering met tests is aangetoond. ‘Privé’ en ‘gewist’ mogen geen cosmetische labels zijn.

## 0.9 — Beta en afwerking

- TalkBack-controle met betekenisvolle labels, knoprollen en schakelaarstatussen.
- Alle belangrijke acties hebben aanraakgebieden van minimaal 48 dp, zichtbare toetsenbordfocus en bruikbaar contrast.
- Grote lettertypen tot 200%, smalle schermen, schermrotatie en Android-systeembalken testen.
- Nederlandse teksten consistent maken en daarna volledige Engelse lokalisatie toevoegen.
- Foutmeldingen tonen wat de gebruiker kan doen: opnieuw proberen, een instelling wijzigen of een extensie uitschakelen.
- Geteste gegevensmigraties tussen versies, een transparant wijzigingslog en bekende beperkingen per release.
- Handmatige compatibiliteitstest met veelgebruikte sites, webformulieren, media en Android Autofill.
- Een onderhoudsritme voor GeckoView, Material Components en de lokale blokkeerlijst vastleggen, inclusief controle van licenties en regressies.

**Klaar wanneer:** er geen open P0-problemen zijn, de belangrijkste flows op de volledige matrix slagen en testers een update met behoud van hun gegevens kunnen uitvoeren.

## Wanneer is 1.0 klaar?

- [ ] Installeren en bijwerken werken met een vaste, veilig beheerde ondertekening.
- [ ] Geen bekende blokkerende opstart-, navigatie- of gegevensverliesproblemen.
- [ ] De volledige instellingen-, browser- en downloadflows zijn op hardware gecontroleerd.
- [ ] Privégedrag en gegevens wissen zijn technisch aantoonbaar, niet alleen beschreven.
- [ ] Ondersteunde extensies en beperkingen zijn eerlijk gedocumenteerd.
- [ ] Toegankelijkheid en handheldbediening zijn meegenomen in de acceptatie.
- [ ] Privacyverklaring, pakketnamen, herkomst van afhankelijkheden en releaseprocedure zijn op orde.
- [ ] Er is een onderhoudsplan voor beveiligingsupdates en een ondersteund kanaal voor bugmeldingen.

## Bewust later of nog te besluiten

- **Synchronisatie en accounts:** pas na een expliciete keuze over end-to-end-versleuteling, sleutelherstel, serverbeheer, kosten en verwijdering. Veyra krijgt geen accountknop die nog niets doet.
- **Ingebouwde wachtwoordkluis:** geen huidige prioriteit; eerst goede integratie met bestaande Android-providers.
- **Rechtstreekse website-passkeys:** afhankelijk van de vereiste browser-/providergoedkeuring en een geteste integratie.
- **Play Store-publicatie:** afhankelijk van vaste ondertekening, privacyinformatie en de releasecriteria; geen aangekondigde datum voordat die voorwaarden zijn ingevuld.
- **Nieuwe engine of grote frameworkmigratie:** alleen wanneer een concrete beperking en meetbare voordelen dat rechtvaardigen.

## Werkwijze per volgende ontwikkelronde

1. Kies één samenhangende mijlpaal en benoem wat er aan het eind daadwerkelijk werkt.
2. Houd bestaande gebruikersgegevens en beveiligingscontroles intact.
3. Bouw de volledige flow, inclusief fouten, lege toestanden en terugnavigatie.
4. Voer gerichte tests uit; leg afzonderlijk vast wat lokaal, in CI en op hardware is gecontroleerd.
5. Werk roadmap en wijzigingslog bij op basis van het resultaat.
6. Publiceer pas na de bijbehorende vrijgavecontrole; een ontwikkelcommit is nog geen nieuwe APK-release.
