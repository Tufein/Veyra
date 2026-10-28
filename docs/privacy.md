# Privacy en gegevens in Veyra

Van toepassing op 1.0.0-rc.2. Veyra heeft geen toegevoegde analytics en geen eigen gehoste accountdienst. GeckoView levert de webengine; websites, Mozilla-diensten, je zoekaanbieder en externe apps verwerken hun eigen netwerkverzoeken. Gecko kan onder meer Safe Browsing-verkeer uitvoeren. Veyra is geen anonimiteitsdienst.

## Normaal browsen

Voorkeuren, bladwijzers, maximaal acht snelkoppelingen, normale tabadressen en maximaal 2000 recente unieke geschiedenisadressen staan lokaal in appopslag. Websitecookies, opslag en cache staan in het Gecko-profiel. Normale tabs herstellen adressen; volledige formulierstatus na afsluiten is niet beloofd. Certificaatfouten worden niet omzeild. Third-party cookies en mixed content zijn beperkt; HTTPS-only weigert HTTP-navigatie en gewone onderschepte HTTP-verzoeken. OS-backup en apparaattransfer van appdata zijn uitgeschakeld.

## Privétabs

Privétabs gebruiken Gecko-private sessies met afzonderlijke contexten. Hun adressen en titels worden niet in normale tabbackups of geschiedenis geschreven. De eigen blokkeeruitzonderingen voor privétabs blijven in geheugen; geïnstalleerde gebruikersuitbreidingen en Android Autofill worden in privétabs niet ingeschakeld. De eigen ingebouwde beschermingsmodule is daar wel actief. Een inactieve private anchorsessie houdt private websitecookies tijdens geheugenopruiming binnen dezelfde open privésessie bruikbaar; na de laatste privétab sluit die context.

Zolang privétabs bestaan zet Veyra de Android secure-window-vlag om screenshots/recentsbeelden te beperken. Dit schermt netwerkverkeer niet af voor websites, netwerkbeheer, providers of een gecompromitteerd toestel. Zelf bewaarde bladwijzers, gekopieerde tekst en bewust opgeslagen bestanden kunnen blijven bestaan. Een private download bewaart geen bronadres, maar het gekozen bestand en de bestandsnaam zijn expliciet blijvende uitvoer. Volledige isolatie bij crashes/procesbeëindiging moet op hardware worden gecontroleerd.

InPrivate is herkenbaar aan de blijvende balk **InPrivate actief**, een aparte startpagina, een anders gekleurde adresbalk en het masker in het taboverzicht. De balk blijft staan wanneer browserknoppen bij fullscreen media verdwijnen. De verbindingsindicator blijft afzonderlijk HTTP/HTTPS tonen: privé betekent geen versleutelde verbinding.

**Alle InPrivate-tabbladen sluiten** vraagt bevestiging, sluit uitsluitend privétabs en beëindigt de open privésessie. Dupliceren behoudt het oorspronkelijke type tabblad. **Normaal tabblad heropenen** bewaart maximaal vijf normale webadressen tijdelijk in het geheugen; privétabs en startpagina’s worden niet bijgehouden. Deze lijst verdwijnt bij het sluiten/hermaken van de browseractiviteit en wordt ongeldig bij geschiedenisverwijdering of de actie voor websitegegevens wissen. Ze wordt niet geëxporteerd of gesynchroniseerd. Privé gekopieerde links krijgen vanaf Android 13 de gevoeligheidsmarkering voor het klembord; de kopie zelf kan blijven bestaan.

## Downloads en toestemmingen

Downloads gebruiken het antwoord dat Gecko al ontving, inclusief de bestaande websitesessie: Veyra doet geen tweede download met geëxporteerde cookies. Androids bestandskiezer bepaalt waar een bestand wordt opgeslagen. Voortgang en beperkte metadata staan lokaal; afgebroken gedeeltelijke bestanden worden indien mogelijk verwijderd en anders gemeld. Een opnieuw-aanvragenactie opent de website opnieuw; eerdere POST-gegevens worden niet automatisch herhaald. **Vergeten** verwijdert alleen een downloadregistratie, niet je opgeslagen bestand.

Camera, microfoon en locatie vereisen een websitekeuze en de Android-systeemtoestemming. Normale beslissingen horen bij een exacte HTTPS-origin inclusief poort. Private beslissingen worden niet blijvend in Veyra opgeslagen. Schermopname wordt niet aangeboden. De interface laat actieve camera/microfoonopname stoppen door de betrokken pagina’s te sluiten. Androids appinstellingen blijven de uiteindelijke systeemtoegang bepalen.

## Websitegegevens en wissen

Websitebeheer toont verleende rechten, bekende hosts en aantallen normale cookies. De eigen ingebouwde extensie geeft uitsluitend cookietellingen terug, geen cookiewaarden en geen private cookies. Dit is geen volledige schijfmeting van alle websiteopslag.

Privacy en gegevens biedt afzonderlijke bevestigde keuzes voor geschiedenis, cookies/siteopslag, cache, rechten en persoonlijke voorkeuren. Rechtenreset sluit open pagina’s en reset ook enginebeslissingen. Een gerichte websitereset gebruikt Gecko’s hostopslagfunctie. Bladwijzers en startpagina blijven bij voorkeurenreset behouden. Opgeslagen bestanden en externe backups worden niet automatisch gewist. Voor alle appgegevens gebruik je Android **Apps → Veyra → Opslag → Gegevens wissen**; ook dat wist geen bestanden buiten appopslag of bestanden op je syncserver.

## Wachtwoorden, extensies en delen

Veyra heeft geen eigen wachtwoordkluis. Android Autofill gebruikt je gekozen provider in normale tabs. Passkey-inloggen kan in een andere browser; die sessie wordt niet terug naar Veyra gekopieerd. Rechtstreekse willekeurige website-passkeys vereisen een nog ontbrekende providerintegratie.

Extensies komen uit de Mozilla Add-ons-catalogus en vereisen ondertekening en expliciete toestemming. Zij kunnen toegang hebben tot bezochte websites volgens de verleende rechten; controleer hun bron en gegevensverklaringen. Alleen Veyra’s eigen achtergrondmodule krijgt de beperkte native beschermings-/cookietellingsbridge.

Lokale backups zijn leesbare JSON: bewaar ze privé. Ze bevatten bladwijzers, snelkoppelingen en optioneel ondersteunde voorkeuren/normale tabadressen; geen cookies, wachtwoorden of privésessies. Optionele [synchronisatie](sync.md) verstuurt uitsluitend versleutelde bladwijzers en snelkoppelingen naar je eigen server. **Verbinding vergeten** verwijdert geen extern serverbestand. Rapporteer fouten zonder persoonlijke URLs, tokens, cookies of herstelzinnen; zie [SECURITY.md](../SECURITY.md).
