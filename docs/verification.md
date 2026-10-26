# Controle van versie 0.1.0

Datum: 26 oktober 2026.

## Bouw en analyse

- `:app:assembleDebug`: geslaagd met Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 21 en Android SDK 35.
- `:app:testDebugUnitTest`: drie tests geslaagd, nul fouten. Ze bevatten meerdere gevallen voor domeinen, zoektermen, Unicode, IPv6, onveilige URL-schema's, misleidende adressen en domeingrenzen bij blokkering.
- `:app:lintDebug`: nul fouten en nul waarschuwingen.
- De debug-APK is gecontroleerd met `apksigner verify` en heeft een Android-debughandtekening. Voor Play Store-publicatie is een eigen releasehandtekening nodig.
- `aapt dump badging` bevestigt pakket `nl.veyra.browser`, versie `0.1.0`, minimum API 28, doel-API 35 en uitsluitend de manifesttoestemming `INTERNET`.

## Runtime

Een runtimecontrole is geprobeerd met een Android 15-emulator (API 35, x86_64), zonder hardwareversnelling. De emulator kwam tijdens ongeveer tien minuten opstarten niet tot een bruikbare Android-sessie. Installatiepogingen faalden in Androids pakketbeheer: eerst een niet-geïnitialiseerde `PackageManagerInternal` in `StorageManagerService`, daarna `Failure calling service package: Broken pipe (32)`. De app zelf is daarbij niet gestart.

**De interface en het browsen zijn hier dus niet tijdens uitvoering geverifieerd.** Het meegeleverde script `tools/smoke_test.py` is op Python-syntaxis gecontroleerd, maar de volledige rooktest is niet uitgevoerd. Er zijn geen screenshots of geslaagde runtimeresultaten geclaimd. Controleer de APK op een echte telefoon of een werkende emulator voordat je hem als dagelijkse browser gebruikt.

Nog niet gecontroleerd: echte Android-telefoons, alle ondersteunde Android-versies, echte websitecompatibiliteit, aanmelden bij diensten, uploads, langdurig gebruik, toegankelijkheid met TalkBack, batterijverbruik en vergelijking met Samsung Internet. De app bevat nog geen gemeten claim over snelheidswinst.
