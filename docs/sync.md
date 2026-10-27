# Versleutelde synchronisatie

De 0.5.0-kandidaat heeft handmatige synchronisatie van bladwijzers en startpaginakoppelingen met een eigen HTTPS-bestandserver, bijvoorbeeld een passende WebDAV-server. De server moet een definitief HTTPS-bestandsadres, GET, PUT, sterke ETags en voorwaardelijke schrijfacties ondersteunen. Een gewone webpagina, Firefox-account of alleen een gedeelde map is daarvoor niet voldoende. Veyra levert geen gehoste accountdienst; de eigenaar beheert server, toegang, backups en verwijdering.

Open **Instellingen → Browser en gegevens → Versleutelde synchronisatie**. Vul het bestandsadres, eventuele HTTP Basic-inloggegevens en een lange unieke herstelzin in. Gebruik op elk apparaat hetzelfde bestand en dezelfde herstelzin. Minimaal twaalf tekens wordt afgedwongen; een lange willekeurige zin is verstandiger. Bewaar die buiten Veyra in je wachtwoordmanager: er bestaat geen herstel via e-mail.

## Welke gegevens reizen mee?

Alleen de bladwijzers en startpaginakoppelingen gaan in de versleutelde inhoud. Geschiedenis, tabs, wachtwoorden, cookies, downloads, websitepermissies en voorkeuren synchroniseren niet. De server ziet wel je verbinding, account, bestandsnaam, grootte, tijden en versleutelde bytes.

De client gebruikt AES-256-GCM, een nieuwe willekeurige salt van 16 bytes, een nonce van 12 bytes en een authenticatietag van 128 bits. PBKDF2-HMAC-SHA256 met 600.000 iteraties leidt de sleutel af van de herstelzin. Het versie-1-envelope authenticeert het protocol via vaste additional authenticated data. Ongeldige versies, te grote bestanden, verkeerde herstelzinnen en gewijzigde ciphertext worden geweigerd vóór import. Dit is een geteste implementatie, geen onafhankelijke cryptografische audit.

HTTPS-certificaatcontrole blijft aan. Doorverwijzingen worden geweigerd om accountgegevens niet naar een andere bestemming te sturen. GET leest maximaal 4 MiB envelope; ontsleutelde backups zijn maximaal 2 MiB. De server ontvangt nooit de herstelzin als protocolveld.

## Samenvoegen en conflicten

Na een succesvolle synchronisatie bewaart het apparaat een lokale basisversie per server/account. Volgende synchronisaties vergelijken die basis met lokaal en server. Hierdoor kunnen toevoegingen, naamwijzigingen, volgorde en verwijderingen doorwerken. Gelijktijdige verschillende wijzigingen vragen een expliciete keuze voor lokale of servergegevens; daarna volgt een bevestiging van de aantallen en lokale verwijderingen. Gekoppelde bladwijzers en startpaginanamen blijven afgestemd. De limieten zijn 5000 bladwijzers en acht startpaginakoppelingen; een te grote combinatie stopt zonder gedeeltelijke import.

Bij de eerste koppeling bestaat nog geen gemeenschappelijke basis. Dan wordt toevoegend samengevoegd; oude verwijderingen zijn niet af te leiden. Een volledig nieuwe installatie kan de serververzameling overnemen zonder de vier voorbeeldkoppelingen toe te voegen. Een naambewerking volgt dezelfde website-URL; een URL-wijziging verschijnt als verwijderen/toevoegen.

Een serverbestand wordt gewijzigd met `If-Match` en de gelezen ETag, of bij een nieuw bestand met `If-None-Match: *`. HTTP 409/412 breekt af. Als de lokale verzameling intussen verandert, overschrijft de client die niet en vraagt opnieuw synchroniseren. Een server moet deze HTTP-voorwaarden correct uitvoeren. Een kwaadwillende server kan bestanden verwijderen of oude versies teruggeven; versleuteling voorkomt niet beschikbaarheidsverlies of rollback.

## Bewaren en vergeten

Verbindingsgegevens worden alleen opgeslagen wanneer je dit aanvinkt. Android Keystore versleutelt die lokale gegevens; er is geen plaintext export in de Veyra-backup. Zonder deze keuze moet je opnieuw invullen. **Verbinding vergeten** wist lokale verbindingsgegevens en de basisversie, niet het serverbestand. Verwijder dat afzonderlijk op je server. Een eigen server kan accountwachtwoorden kennen; gebruik daarom een aparte sterke herstelzin voor de inhoud.

Automatische tests controleren cryptografie, grenzen, TLS/redirectbeleid, ETags en samenvoegen. Een echte tweetelefoontest tegen een beheerde server is hier niet uitgevoerd; zie [apparaatvalidatie](device-validation.md).
