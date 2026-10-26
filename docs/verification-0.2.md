# Controle van Veyra 0.2.0

Datum: 26 oktober 2026.

## Geslaagde controles

- `:app:assembleDebug`, met Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 21 en SDK 35.
- `:app:testDebugUnitTest`: zeven tests, nul fouten. Gevallen omvatten veilige adresverwerking, Unicode en IPv6, een andere zoekmachine zonder bestaande URL's te wijzigen, HTTPS-beleid, expliciete protocolwisseling met behoud van pad/query, domeingrenzen, first-party-uitzonderingen en het inlezen van de echte gebundelde blokkeerlijst.
- `:app:lintDebug`: nul fouten en nul waarschuwingen.
- APK-handtekening gecontroleerd met `apksigner verify`. Het SHA-256-certificaat is gelijk aan dat van de eerder geleverde 0.1.0-APK. Pakketnaam blijft `nl.veyra.browser`; versionCode is verhoogd naar 2 en versionName naar 0.2.0. Dit ondersteunt installeren als update met behoud van appgegevens.
- `aapt dump badging` bevestigt minimum API 28, doel-API 35 en alleen de toestemming `INTERNET`.
- De APK bevat de domeinlijst, bronattributies en licentieteksten. De gewijzigde lijst bevat 56.339 unieke domeinen, naast de oorspronkelijke basisregels.

## Grenzen van de controle

Deze update is niet op een werkende emulator of echte telefoon uitgevoerd. Er zijn geen nieuwe screenshots of geslaagde runtimetests geclaimd. De eerdere emulatorpoging is beschreven in `verification.md`; die was geen controle van 0.2.0. De gebruikersmelding dat 0.1.0 goed werkt is geen verificatie van deze update.

Nog op een telefoon controleren:

1. Installeer 0.2.0 als update en controleer bestaande tabs, cookies en bladwijzers.
2. Wissel donker/licht/systeem en alle accentkleuren. Controleer instellingen, toetsenbord, draaien van de telefoon en schermen met grote systeemtekst.
3. Bewaar naam, snelkoppelingen, zoekmachine en tekstgrootte. Controleer herstel na opnieuw openen.
4. Controleer een bekende advertentiewebsite met blokkering aan, uit en als website-uitzondering. De implementatie garandeert geen volledige blokkering van advertenties of tracking.
5. Kies alleen HTTPS en probeer een expliciet HTTP-adres, een redirect naar HTTP en een HTTP-link vanuit een andere app. De pagina moet geblokkeerd blijven. Probeer vervolgens expliciet HTTPS via de verbindingsindicator. Controleer opnieuw met de normale voorkeur en de bevestigde HTTP-keuze.
6. Kies een vertrouwde Android Autofill-provider en controleer een inlogpagina met een testaccount. Controleer dat de provider de juiste website herkent en zijn eigen ontgrendeling gebruikt. Test meer dan één provider voordat brede compatibiliteit wordt geclaimd.
7. Open een HTTPS-inlogpagina via ‘Inloggen met een externe passkey’, kies een andere browser en test de passkey daar. Controleer dat Veyra niet terug in de keuzelijst staat en dat geen login of sessie naar Veyra wordt overgezet.

Native passkeys voor willekeurige websites binnen Veyra zijn niet geïmplementeerd: daarvoor is goedkeuring als browser door de credential-provider nodig. Er is geen eigen wachtwoordkluis en geen JavaScript-bridge die wachtwoorden of passkeys opvraagt. De huidige integratie bestaat uit Android Autofill en een expliciete externe-browserroute.
