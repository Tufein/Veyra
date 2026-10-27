# Ingebouwde blokkeerlijst

`app/src/main/assets/adblock-hosts.txt` is een subset van de snapshot van [StevenBlack/hosts](https://github.com/StevenBlack/hosts), opgehaald op 26 oktober 2026. Het bestand bevat 56.339 unieke domeinen. Veyra voegt daarnaast zijn bestaande kleine basislijst toe.

Wijziging: de volledige bronsecties `mvps.org` en `someonewhocares.org` zijn weggelaten vanwege hun voorwaarden voor uitsluitend niet-commercieel gebruik. Het oorspronkelijke aantal in de upstream-header beschrijft de volledige bronlijst; de extra Veyra-header beschrijft de gebundelde subset. De overige bronsecties, auteursvermeldingen, bronlinks en licentieverwijzingen zijn behouden. Deze data worden niet op de telefoon automatisch bijgewerkt.

Bronbestand: https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts

De MIT-licentie van Steven Black staat in `assets/adblock-license.txt`. De onderdelen van AdAway noemen CC BY 3.0, en de KADhosts-data van azet12 / Filters Heroes noemen CC BY-SA 4.0. De volledige teksten zijn gebundeld als `assets/CC-BY-3.0.txt` en `assets/CC-BY-SA-4.0.txt`. Voor de aangepaste collectie gelden CC BY-SA 4.0 en de in de bronsecties behouden rechten en attributies. De afzonderlijke licenties van de brongegevens blijven gelden; dit document kent geen licentie toe aan de eigen Veyra-appcode.

Attributie: Steven Black en de in de lijst genoemde bijdragen; AdAway (Kicelo, Dominik Schuermann en bijdragers); azet12 / Filters Heroes (KADhosts); en de overige in de bewaarde bronsecties genoemde auteurs. De ongeminificeerde bronlijst, wijzigingen en licentieteksten zijn zowel in de APK als in het Android Studio-project beschikbaar.


## Browser engine and UI

- Mozilla GeckoView `158.0.20261021001945`: Mozilla Public License 2.0 and the third-party notices shipped with Gecko. Source archive: <https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/158.0.20261021001945/geckoview-158.0.20261021001945-sources.jar>. GeckoView runs installed extensions under their own licenses; those packages are not bundled with Veyra.
- Material Components for Android `1.12.0` and AndroidX dependencies: Apache License 2.0. <https://github.com/material-components/material-components-android> and <https://android.googlesource.com/platform/frameworks/support/>.
- The Veyra protection extension packages the same attributed blocklist described above. It does not fetch or redistribute a third-party extension.
