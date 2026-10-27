# Vaste ondertekening en migratie

De 0.5.0-kandidaat introduceert één nieuwe vaste sleutel, op uitdrukkelijk verzoek van de eigenaar. De privésleutels van eerdere openbare CI-alpha’s zijn niet bewaard. Een certificaat uit een APK bevat alleen de publieke sleutel: hiermee kan geen compatibele nieuwe APK worden ondertekend.

Publieke SHA-256-certificaatvingerafdruk:

```
0a35ce54abed29880b6380ac1eedfa6f9d332d7df923545d52d6de50a9bfdfbe
```

Dit is de certificaatvingerafdruk, geen APK-bestandschecksum. De machineleesbare waarde staat in [signing-certificate-sha256.txt](signing-certificate-sha256.txt).

## Eerst gegevens veiligstellen

**Verwijder een bestaande installatie niet voordat je een bruikbare backup hebt gecontroleerd.** Android staat geen update met een andere ondertekeningssleutel toe. De pakketnaam blijft `nl.veyra.browser`; een andere pakketnaam zou de oude gegevens ook niet vanzelf overnemen.

De oudere openbare alpha is debuggable. Gebruik op je eigen computer Python 3.11+ en Android Platform Tools, verbind je eigen telefoon via USB en accepteer de Android-debugautorisatie. Sluit Veyra eerst normaal af zodat voorkeuren zijn opgeslagen.

```sh
python tools/export_legacy_data.py --serial DEVICE --output veyra-backup.json --include-tabs
```

De tool leest uitsluitend `shared_prefs/veyra.xml` met Android `run-as`. Hij verwijdert niets, verandert de oude installatie niet en overschrijft geen bestaand uitvoerbestand. Bewaar het JSON-bestand privé. Zonder `--include-tabs` worden geen tabadressen meegenomen. Als Android lezen weigert, houd de oude app geïnstalleerd en los de export eerst op.

Controleer in het bestand je bladwijzers, maximaal acht snelkoppelingen en ondersteunde voorkeuren. Wachtwoorden, cookies, ingelogde sessies, extensiegegevens en formulierinhoud worden niet gemigreerd. Normale tabadressen zijn optioneel; privétabs worden geweigerd. Eerdere versies hadden nog geen browsegeschiedenis.

Pas na die controle kun je de oude app zelf verwijderen en de nieuwe APK installeren. Open **Instellingen → Browser en gegevens → Importeren en exporteren**, kies het bestand en controleer de importvoorvertoning. Schakel voorkeuren en normale tabadressen alleen in als je die wilt herstellen. Import is toevoegend; bestaande gekoppelde namen blijven behouden. Als startpagina’s samen meer dan acht websites bevatten, stopt de import zonder gedeeltelijke wijzigingen. Maak dan eerst ruimte. De eerste vier voorbeeldsnelkoppelingen kunnen worden losgemaakt in de bibliotheek.

Open tabs herstellen adressen, geen volledige website- of formulierstatus. Bij de limiet van twaalf tabs blijven extra geïmporteerde normale adressen klaarstaan tot ruimte vrijkomt. Log bij websites opnieuw in via je bestaande wachtwoordmanager.

## Volgende updates

Voor updates moeten pakketnaam, oplopende versiecode en vaste sleutel kloppen. Versie 0.5.0 gebruikt versiecode 7. Alle drie lokale APK’s zijn met het gepinde certificaat gebouwd. Een echte update met gegevensbehoud op hardware blijft een afzonderlijke vrijgavecontrole.

Publieke releases vereisen repository Secrets:

- `VEYRA_KEYSTORE_BASE64`
- `VEYRA_SIGNING_STORE_PASSWORD`
- `VEYRA_SIGNING_KEY_PASSWORD`
- `VEYRA_SIGNING_KEY_ALIAS`

`tools/upload_signing_secrets.py` voert de waarden uitsluitend via stdin naar `gh secret set`; geef paden naar privébestanden op, geen wachtwoorden als opdrachtargument. Zie `--help`. De releaseworkflow stopt als de sleutel ontbreekt of het certificaat afwijkt. De tijdelijke CI-keystore wordt verwijderd voordat rapporten worden geüpload. Gewone ontwikkelbuilds zonder deze configuratie gebruiken hun eigen debugcertificaat en zijn geen compatibele publieke update.

De nieuwe sleutel en wachtwoordbestanden staan lokaal buiten Git en zijn nog niet als GitHub Secrets opgeslagen: de beschikbare GitHub-API-aanmelding geeft `401 Bad credentials`. De API is bij de laatste controle bereikbaar, maar voor secretbeheer is een geldige aanmelding met repository Contents:write en Secrets:write nodig. Ook de Git-push is met de beschikbare credential geweigerd; de broncode is lokaal gecommit. De beveiligde `GH_TOKEN`-vereiste is opgeslagen in de omgevingsconfiguratie; voer de waarde uitsluitend via die instellingen in, niet in chat. Maak daarnaast een onafhankelijke, versleutelde backup in een door de eigenaar beheerde geheime opslag; alleen een tijdelijke cloudworkspace is geen duurzaam sleutelbeheer. Publiceer nooit de privésleutel, wachtwoorden of een onversleutelde backup. Verlies van deze sleutel zou opnieuw een migratie noodzakelijk maken.
