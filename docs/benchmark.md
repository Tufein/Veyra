# Vergelijken met Samsung Internet

Doel: aantonen waar Veyra tijd of geheugen bespaart, en waar niet. Vul echte resultaten pas in na metingen. Een emulator of APK-bestandsgrootte bewijst geen snelheid op een telefoon.

1. Gebruik dezelfde Android-telefoon, Wi-Fi, schermhelderheid en energie-instellingen. Noteer telefoonmodel, Android-versie, Samsung Internet-versie en GeckoView-versie (Veyra) en engineversie (andere browser). Laat de telefoon afkoelen en schakel energiebesparing uit.
2. Vergelijk eerst zonder advertentieblokkering in beide browsers. Meet daarna apart met vergelijkbare blokkering. Noteer de blokkeerlijsten: verschillende lijsten beïnvloeden de resultaten.
3. Meet twintig koude appstarts vanuit een gestopte app. Voor Veyra: `adb shell am force-stop nl.veyra.browser`, daarna `adb shell am start -W -n nl.veyra.browser/.BrowserActivity`. Vergelijk dezelfde startconditie en dezelfde lokale startpagina waar mogelijk. Een lege lokale startpagina en een nieuwsfeed zijn verschillende workloads.
4. Meet tien identieke pagina's: een statische tekstpagina, een nieuwssite en een zware webapp. Herhaal elke pagina minimaal vijf keer en wissel de volgorde van browsers. Houd koude caches en warme caches apart. Meet visueel bruikbare inhoud en navigeerbaarheid; de laadbalk alleen is onvoldoende.
5. Meet geheugengebruik met één, vijf en twaalf geopende tabs na een vaste wachttijd. Gebruik `adb shell dumpsys meminfo nl.veyra.browser` en neem ook de bijbehorende Gecko-content-/GPU-processen mee. Meet dezelfde procesgroepen voor Samsung Internet. Vergelijk ook hoe vaak teruggeschakelde tabs opnieuw laden.
6. Controleer tien minuten normaal gebruik: scrollen, een video, schakelen tussen tabs, terug/vooruit en toetsenbord. Noteer haperingen, gebroken sites, verdwenen formulierinhoud, crashes en batterijgedrag.

Rapporteer mediaan en spreiding per scenario en leg de ruwe meetgegevens vast. Vermeld de telefoon en versies bij iedere snelheidsclaim. Kies vervolgstappen op basis van het grootste gemeten knelpunt.

## Meettool en huidige resultaten

Er zijn hier geen telefoonmetingen uitgevoerd. Gebruik een geïsoleerd testtoestel: de runner force-stopt de gekozen browser en gaat herhaald naar Home. Bestaande tabadressen blijven bestaan, maar een niet-opgeslagen formulier kan verloren gaan.

```sh
python tools/device_benchmark.py --serial DEVICE --runs 20 --output /private/path/startup.json
```

De runner slaat werkelijke `am start -W` TotalTime-metingen, mediaan/min/max en een meminfo-snapshot op. Ontbrekende tijden worden niet verzonnen. Android-launch TotalTime is geen bewijs voor zichtbare eerste inhoud of paginavoltooiing. Meet die afzonderlijk met een vaste fixture en videotrace. Noteer bij elke run netwerk, helderheid, energie/temperatuur, versie, resident-limit en extensies. Bekijk alle bijbehorende engineprocessen en tabreloads bij 1/5/12 tabs; één meminfo-snapshot alleen is geen volledige vergelijking. Meet batterij over een vaste langere taak met dezelfde omstandigheden en rapporteer energie plus spreiding.
