# BG Offers

BG Offers sammelt Brettspielangebote von Spiele-Offensive, Milan-Spiele und dem BGG Market sowie neue Themen aus dem Schnäppchenforum von unknowns.de. Shop- und Market-Angebote werden mit BoardGameGeek- und Vergleichspreisdaten angereichert; unknowns.de-Themen werden ausschließlich mit Titel und Link gespeichert und gemeldet. Ein dauerhaftes Activity Log macht diese Abläufe auch in der Web-Oberfläche nachvollziehbar. Ohne Telegram-Konfiguration werden dieselben Meldungen im Anwendungslog ausgegeben.

Die Anwendung verwendet Java 25, Spring Boot, Maven, H2 mit Flyway, Jsoup, Thymeleaf und Bootstrap als WebJar. Die Web-Oberfläche ist ausschließlich lesend und unter `http://localhost:8080` erreichbar.

## Funktionsumfang

- Liest die Banner innerhalb von `#wrapper_startseite` auf Spiele-Offensive aus.
- Lädt für normale Angebote und Gruppendeals die Detailseite, um Name, regulären oder reduzierten Preis, Verfügbarkeit und die Gruppendeal-Restmenge zu bestimmen. Allgemeine Mindestbestellwert- und Lieferzeit-Hinweise von Spiele-Offensive werden nicht als Verfügbarkeit übernommen. Bei Gruppendeals wird der tatsächlich an den Warenkorb übergebene Preis verwendet, sodass Trostangebote und optionale VIP-Upgrades nicht als Dealpreis erkannt werden.
- Berücksichtigt den vom Server gemeldeten HTML-Zeichensatz, damit deutsche Umlaute korrekt übernommen werden.
- Verwendet bei dynamisch aufgebauten Spiele-Offensive-Bannern das eingebettete Produktbild statt der dekorativen `display_angebot`-Ebene.
- Behandelt Spieleschmiede-Banner ohne BGG- und Preisvergleich und versendet bei konfiguriertem Telegram nur das Bild.
- Ignoriert Inspirations-TV.
- Liest alle Angebotsseiten von Milan-Spiele ein und lädt das große Produktbild von jeder Detailseite. Temporäre HTTP-Fehler werden automatisch erneut versucht; die Detailabrufe sind gedrosselt.
- Liest die neuesten in Deutschland angebotenen Neuware-Artikel aus dem BGG Market ein und speichert sie eindeutig über deren `productid`.
- Übernimmt für BGG-Market-Angebote `version.name`, Preis und Produktlink und verwendet `objectid` direkt für BoardGameGeek und den Preisvergleich. Fehlt bei einem Angebot die Version, dient `objectlink.name` als Namens-Fallback.
- Meldet BGG-Market-Angebote ausschließlich, wenn ihr Preis unter dem aktuell verfügbaren Preis bei brettspiel-angebote.de liegt.
- Prüft das Schnäppchenforum von unknowns.de auf neue Themen und speichert beziehungsweise meldet dafür nur Titel und Link. BoardGameGeek und brettspiel-angebote.de werden für diese Einträge nicht aufgerufen.
- Speichert Angebote, Zeitpunkte, BGG-Werte, Vergleichspreise und den Benachrichtigungsstatus dauerhaft in H2.
- Bereinigt Suchbegriffe für BoardGameGeek und brettspiel-angebote.de: führende und folgende Leerzeichen, Klammerzusätze mit Wörtern sowie die Begriffe „Stapelspiel“, „Würfelspiel“ und „Jubiläumsausgabe“ werden entfernt.
- Überspringt BoardGameGeek und den Preisvergleich vollständig, wenn der Angebotsname „Bundle“ enthält.
- Prüft bei vorhandener BoardGameGeek-ID, dass die von der Preisvergleichssuche gelieferte Detailseite zum selben Spiel gehört.
- Speichert neue Angebote und Preisänderungen mit einer kompakten Vorschau sowie dem damaligen Suchstatus bei brettspiel-angebote.de und BoardGameGeek im Activity Log.
- Wiederholt technische Recherchefehler mit einer Pause, hält jeden erneuten Versuch im Activity Log fest und benachrichtigt erst nach abgeschlossener Recherche oder dem letzten Versuch.
- Protokolliert erfolgreiche und fehlgeschlagene Telegram-Sendeversuche ohne Nachrichteninhalt, Bot-Token oder Chat-ID.
- Lässt nicht verfügbare Werte wie Verfügbarkeit, Vergleichspreis, Bestpreis oder BGG-Daten in Telegram-Nachrichten vollständig weg.
- Meldet neue oder preislich veränderte Angebote, wenn sie günstiger als ein aktuell verfügbares Vergleichsangebot sind oder eine der Zusatzquellen keinen Treffer liefert.
- Verhindert mit einem Fingerabdruck aus Quelle, URL und Preis doppelte Meldungen.
- Aktualisiert alle Quellen alle fünf Minuten.
- Ruft beim Anwendungsstart jede aktivierte Quelle und die „Scythe“-Preissuche auf brettspiel-angebote.de testweise vollständig ab, ohne Ergebnisse zu speichern, und meldet Trefferzahlen, Fehler sowie die kurze Commit-ID per Telegram oder im Anwendungslog.
- Sortiert die Angebotsübersicht absteigend nach der letzten inhaltlichen Aktualisierung.
- Überwacht, wann pro aktiver Quelle zuletzt ein neuer Datensatz gespeichert wurde. Nach vier Tagen ohne neue Daten von Spiele-Offensive, Milan-Spiele oder dem BGG Market beziehungsweise nach 30 Tagen bei unknowns.de wird genau eine Warnung gesendet. Ein späterer neuer Datensatz aktiviert die Warnung für die nächste Ruhephase erneut.
- Ruft die Suche von brettspiel-angebote.de unter `/suche/?s=Suchbegriff` auf und lädt danach gezielt die im `Location`-Header genannte Detailseite.
- Ruft vor der ersten Suche die Startseite von brettspiel-angebote.de über Springs `RestClient` auf. Alle gesetzten Cookies, insbesondere `bunny_shield*`, bleiben im gemeinsamen Cookie-Speicher und werden bei Suche und Detailseite automatisch mitgesendet.
- Protokolliert jeden HTTP-Aufruf des Preisvergleichs auf `DEBUG`: Schritt, Versuch, Methode, URL, bevorzugtes HTTP-Protokoll, Request- und Response-Header, `Location`, Laufzeit, Status, Antwortgröße sowie die vor und nach dem Aufruf gespeicherten Cookies. Fehler bleiben auf `WARN` sichtbar und enthalten zusätzlich den antwortenden Server und den Seitentitel.
- Prüft täglich um 08:00 Uhr Europe/Berlin mit „Scythe“, ob brettspiel-angebote.de weiterhin auswertbar ist.
- Prüft im selben täglichen Lauf mit „Magical Athlete“, ob Daten von BoardGameGeek abgefragt werden können. Nach einem fehlgeschlagenen Test wird beim ersten wieder erfolgreichen Lauf einmalig eine Entwarnung gesendet; weitere erfolgreiche Läufe bleiben still, bis erneut ein Fehler auftritt.

## Voraussetzungen

- JDK 25
- Maven 3.9 oder neuer
- Optional: Telegram-Bot und Chat-ID
- Optional, aber für BGG-Daten erforderlich: persönlicher BGG-API-Token
- Bei einem Betrieb auf einem Cloud-Server: ein VPN mit einer nicht als Cloud-/Rechenzentrums-IP erkannten Ausgangsadresse, zum Beispiel ein WireGuard-Tunnel zur eigenen FRITZ!Box. brettspiel-angebote.de scheint Zugriffe von Cloud-IPs zu sperren.

## Lokal starten

```bash
mvn clean package
java -jar target/bg-offers-0.0.1-SNAPSHOT.jar
```

Alternativ:

```bash
mvn spring-boot:run
```

Der erste Abruf beginnt 15 Sekunden nach dem Start. Die H2-Dateien werden standardmäßig im Verzeichnis `./data` abgelegt. Flyway erstellt das Datenbankschema beim ersten Start und führt ausstehende Migrationen aus `src/main/resources/db/migration` automatisch aus. Eine bereits von einer älteren Version angelegte Datenbank wird beim ersten Start mit Flyway als Version 1 registriert und unverändert weiterverwendet. Hibernate validiert das migrierte Schema beim Start, nimmt aber selbst keine Schemaänderungen mehr vor.

## Konfiguration

Die wichtigsten Einstellungen können als Umgebungsvariablen gesetzt werden:

| Variable | Bedeutung | Standard |
|---|---|---|
| `INITIAL_IMPORT` | Unterdrückt Angebotsmeldungen für zwei Stunden nach dem Anwendungsstart | `false` |
| `STARTUP_SYSTEM_CHECK_ENABLED` | Führt den rein lesenden Quellencheck beim Start aus | `true` |
| `GIT_COMMIT` | Commit-ID für die Statusmeldung des Start-Systemchecks | `unknown` |
| `TELEGRAM_BOT_TOKEN` | Token des Telegram-Bots | leer, Ausgabe auf der Konsole |
| `TELEGRAM_CHAT_ID` | Ziel-Chat oder Kanal | leer, Ausgabe auf der Konsole |
| `BGG_API_TOKEN` | API-Token für BoardGameGeek | leer, BGG-Status „Nicht konfiguriert“ |
| `SPIELE_OFFENSIVE_ENABLED` | Abruf von Spiele-Offensive aktivieren | `true` |
| `MILAN_ENABLED` | Abruf von Milan-Spiele aktivieren | `true` |
| `BGG_MARKET_ENABLED` | Abruf des BGG Market aktivieren | `true` |
| `UNKNOWNS_ENABLED` | Abruf des unknowns.de-Schnäppchenforums aktivieren | `true` |
| `SPIELE_OFFENSIVE_MAX_SILENCE` | Zeit ohne neue Spiele-Offensive-Daten bis zur Warnung | `4d` |
| `MILAN_MAX_SILENCE` | Zeit ohne neue Milan-Daten bis zur Warnung | `4d` |
| `BGG_MARKET_MAX_SILENCE` | Zeit ohne neue BGG-Market-Daten bis zur Warnung | `4d` |
| `UNKNOWNS_MAX_SILENCE` | Zeit ohne neue unknowns.de-Daten bis zur Warnung | `30d` |
| `UNKNOWNS_USERNAME` | Benutzername oder E-Mail-Adresse für unknowns.de | leer |
| `UNKNOWNS_PASSWORD` | Passwort für unknowns.de | leer |
| `DB_PATH` | Pfad der H2-Datenbank ohne Dateiendung | `./data/bg-offers` |
| `DB_USER` | H2-Benutzer | `sa` |
| `DB_PASSWORD` | H2-Passwort | leer |
| `SERVER_PORT` | HTTP-Port der Anwendung | `8080` |
| `PRICE_COMPARISON_LOG_LEVEL` | Gemeinsames Log-Level für das Preisvergleichsmodul | `INFO` |

### Telegram-Bot-Token und Chat-ID ermitteln

1. In Telegram den verifizierten Bot [@BotFather](https://t.me/BotFather) öffnen, `/newbot` senden und den Anweisungen folgen. BotFather liefert anschließend einen Token im Format `123456789:ABC...`. Dieser vollständige Wert wird als `TELEGRAM_BOT_TOKEN` verwendet. Die Zahl vor dem Doppelpunkt ist die numerische Bot-ID; sie allein reicht für die Anwendung nicht aus.
2. Den neu erstellten Bot öffnen und mindestens eine Nachricht, beispielsweise `/start`, an ihn senden. Für eine Gruppe den Bot zur Gruppe hinzufügen und dort eine Nachricht an ihn senden. Für einen Kanal muss der Bot als Administrator hinzugefügt und eine Nachricht im Kanal veröffentlicht werden.
3. Den Token lokal als Umgebungsvariable setzen und die Telegram-Updates abrufen:

```bash
export TELEGRAM_BOT_TOKEN="123456789:ABC..."
curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates"
```

Die gesuchte `TELEGRAM_CHAT_ID` steht in der Antwort unter `result[].message.chat.id`. Bei Kanalbeiträgen steht sie unter `result[].channel_post.chat.id`. Private Chat-IDs sind üblicherweise positiv, Gruppen- und Kanal-IDs häufig negativ und beginnen bei Supergruppen beziehungsweise Kanälen meist mit `-100`. Falls `result` leer ist, nach dem Senden einer neuen Nachricht erneut aufrufen. Ein bereits für den Bot eingerichteter Webhook kann `getUpdates` blockieren.

Optional lässt sich die Bot-ID mit demselben Token direkt prüfen:

```bash
curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe"
```

Sie steht in der Antwort unter `result.id`. Der Bot-Token ist ein Zugangsschlüssel und darf weder in die Versionsverwaltung noch in Logs oder Screenshots gelangen. Falls er offengelegt wurde, kann er über BotFather widerrufen und neu erzeugt werden.

Die Quellen lassen sich außerdem direkt mit den Spring-Properties `offers.sources.spiele-offensive-enabled`, `offers.sources.milan-enabled`, `offers.sources.bgg-market-enabled` und `offers.sources.unknowns-enabled` einzeln ein- oder ausschalten. Alle vier sind standardmäßig aktiviert. Weitere Einstellungen wie Quell-URLs, Zeitpläne, HTTP-Timeout, Wiederholungsversuche und Parallelität der Milan-Detailabrufe befinden sich in `src/main/resources/application.yml` und können über die üblichen Spring-Boot-Konfigurationsmechanismen überschrieben werden. Temporäre HTTP-Fehler werden standardmäßig bis zu dreimal mit 750 Millisekunden Pause versucht. Die übergeordnete Recherche bei BoardGameGeek und brettspiel-angebote.de wird bei technischen Fehlern ebenfalls bis zu dreimal wiederholt, wartet zwischen den Versuchen aber zehn Sekunden. Diese Pause lässt sich über `LOOKUP_RETRY_DELAY` anpassen, beispielsweise mit `LOOKUP_RETRY_DELAY=30s`. Höchstens zwei Milan-Detailseiten werden gleichzeitig geladen. Die HTTP-Sitzung für brettspiel-angebote.de ist ausschließlich auf den konfigurierten Ursprung beschränkt und wird nicht zwischen Anwendungsstarts gespeichert.

Das Schnäppchenforum von unknowns.de ist derzeit nur für angemeldete Benutzer erreichbar. Vor jedem Abruf meldet sich die Anwendung mit `UNKNOWNS_USERNAME` und `UNKNOWNS_PASSWORD` über das Login-Formular an. Die dabei gesetzten Session-Cookies werden ausschließlich im Arbeitsspeicher verwaltet und automatisch beim anschließenden Forenabruf mitgesendet. Die Zugangsdaten gehören nicht in die Versionsverwaltung oder in Logs. Wird die Quelle nicht benötigt, kann sie mit `UNKNOWNS_ENABLED=false` deaktiviert werden.

Mit `INITIAL_IMPORT=true` werden Angebote während der ersten zwei Stunden nach dem Anwendungsstart weiterhin vollständig importiert, angereichert und im Activity Log erfasst, aber nicht gemeldet. Danach werden Benachrichtigungen automatisch wieder aktiviert, auch wenn die Variable weiterhin auf `true` steht. Bereits während des Zeitfensters importierte, unveränderte Angebote werden nicht nachträglich gemeldet; Benachrichtigungen beginnen mit neuen Angeboten oder Preisänderungen.

## Web-Oberfläche

Die Angebotsübersicht ist unter `http://localhost:8080/` erreichbar. Das paginierte Activity Log befindet sich unter `http://localhost:8080/aktivitaeten` und ist direkt über die Hauptnavigation verlinkt. Es zeigt für Angebotsereignisse Bild, Name, Preisentwicklung, Quelle, Recherche-Retries sowie die Ergebnisse von brettspiel-angebote.de und BoardGameGeek. Telegram-Einträge enthalten ausschließlich Zeitpunkt und Zustellstatus.

Unter `http://localhost:8080/fehlende-treffer` stehen zwei getrennte Listen der bereinigten Suchbegriffe, für die BoardGameGeek beziehungsweise brettspiel-angebote.de keinen Treffer geliefert hat. Technische Fehler, nicht konfigurierte Quellen und übersprungene Bundle-Angebote werden dort nicht als fehlende Treffer gezählt.

Übersprungene Recherchen werden in der Oberfläche nicht mit dem Status „Nicht erforderlich“ dargestellt; der jeweilige Bereich bleibt stattdessen ausgeblendet.

BGG stellt API-Zugriffe nur mit einem gültigen Token bereit. Das verwendete [bggClient-Projekt](https://github.com/jensGiehl/bggClient) beschreibt die Einbindung des Tokens.

## Telegram-Verhalten

Normale Meldungen enthalten kompakt Name, Angebotspreis, Verfügbarkeit, verfügbaren Vergleichspreis, historischen Bestpreis, BGG-Bewertung, „Want to buy“, „Want in trade“ sowie klickbare Links. Wenn ein Angebotsbild vorhanden ist, wird es direkt als Telegram-Foto mit Beschriftung gesendet.

Eine Meldung gilt erst dann als versendet, wenn Telegram den Aufruf erfolgreich bestätigt hat. Fehlerhafte Sendeversuche werden deshalb beim nächsten relevanten Lauf erneut versucht. Ohne Telegram-Konfiguration gilt die Ausgabe im Log als erfolgreiche lokale Meldung.

Direkt beim Start führt die Anwendung einen rein lesenden Systemcheck aus. Dafür ruft sie jeden aktivierten Scraper einmal vollständig auf und prüft, ob mindestens ein Ergebnis geliefert wird. Zusätzlich führt sie wie beim täglichen Health-Check die Suche nach „Scythe“ auf brettspiel-angebote.de aus und erwartet verfügbare Preisdaten. Die dabei gefundenen Angebote und Prüfergebnisse werden weder gespeichert noch angereichert oder als einzelne Angebote gemeldet. Nach Abschluss wird genau eine Statusmeldung mit der Trefferzahl je Quelle, dem Ergebnis des Preisvergleichstests, möglichen Fehlern und der siebenstelligen Commit-ID versendet. Ohne Telegram-Konfiguration erscheint dieselbe Meldung im Anwendungslog.

Die täglichen Health-Checks melden fehlgeschlagene Zugriffe auf brettspiel-angebote.de und BoardGameGeek. Sobald ein betroffener Test wieder erfolgreich ist, folgt genau eine Telegram-Entwarnung. Bleibt der Test erfolgreich, werden keine weiteren Entwarnungen gesendet. Dieser Zustand wird dauerhaft in der Datenbank gespeichert und überlebt Anwendungsneustarts. Schlägt die Zustellung der Entwarnung fehl, wird sie beim nächsten erfolgreichen Lauf erneut versucht. Die Ruhezeit-Überwachung berücksichtigt nur aktivierte Scraper und wertet einen erstmals gespeicherten Eintrag als neue Daten. Ihr Alarmzustand liegt dauerhaft in der Datenbank: Während derselben Ruhephase wird nur einmal gewarnt, nach einem neuen Datensatz kann eine spätere Ruhephase erneut eine Warnung auslösen.

## Docker

Das Image wird einschließlich Tests gebaut:

```bash
docker build \
  --build-arg GIT_COMMIT="$(git rev-parse HEAD)" \
  -t bg-offers:local .
```

Beim veröffentlichten Image übergibt der GitHub-Workflow die Commit-ID automatisch an den Build.

Das in der GitHub Container Registry veröffentlichte Image unterstützt `linux/amd64` und `linux/arm64`, unter anderem für aktuelle 64-Bit-Raspberry-Pi-Systeme. Für den Betrieb kann das folgende Skript verwendet werden. `8089` ist dabei der Port auf dem Host; innerhalb des Containers läuft die Anwendung auf Port `8080`. Das Verzeichnis `./data` wird eingebunden, damit die H2-Datenbank beim Ersetzen des Containers erhalten bleibt.

```bash
docker rm -f bg-offers 2>/dev/null

docker run -d \
  --name bg-offers \
  --pull=always \
  --init \
  -p 8089:8080 \
  -v "$(pwd)/data:/app/data" \
  -e INITIAL_IMPORT="false" \
  -e BGG_MARKET_ENABLED="true" \
  -e BGG_API_TOKEN="BGG_TOKEN" \
  -e TELEGRAM_BOT_TOKEN="BOT_TOKEN" \
  -e TELEGRAM_CHAT_ID="CHAT_ID" \
  -e UNKNOWNS_USERNAME="BENUTZERNAME_ODER_EMAIL" \
  -e UNKNOWNS_PASSWORD="PASSWORT" \
  ghcr.io/jensgiehl/bg-offers:latest
```

Die Web-Oberfläche ist anschließend unter `http://localhost:8089` erreichbar. Wer das lokal gebaute Image statt eines Registry-Images verwendet, ersetzt die letzte Zeile durch `bg-offers:local` und entfernt `--pull=always`.

### Docker über VPN beziehungsweise WireGuard betreiben

Ein HTTP 403 allein belegt keine Sperre der Ausgangs-IP. Als Ursachen kommen unter anderem Schutzregeln für die konkrete Anfrage, den TLS-Client oder den Netzwerkausgang infrage. Ein VPN ist ein möglicher Vergleichstest, wenn der Netzwerkausgang als Einflussfaktor untersucht werden soll; es garantiert keinen erfolgreichen Abruf. Ein WireGuard-Tunnel zur eigenen FRITZ!Box lässt die Abrufe über den heimischen Anschluss laufen. Auch im Heimnetz können einzelne Geräte unterschiedliche Ergebnisse liefern.

In der FRITZ!Box wird unter **Internet → Freigaben → VPN (WireGuard) → WireGuard-Verbindung hinzufügen → Einzelgerät verbinden** eine eigene Verbindung für den Docker-Host angelegt. Anschließend wird die Konfigurationsdatei heruntergeladen. Die FRITZ!Box benötigt dafür eine öffentliche IPv4-Adresse oder eine per IPv6 erreichbare Verbindung; der Docker-Host muss das jeweilige Protokoll ebenfalls nutzen können. Eine ausführliche Anleitung bietet [FRITZ!](https://fritz.com/apps/knowledge-base/FRITZ-Box-7682/3685_WireGuard-VPN-zur-FRITZ-Box-am-Computer-einrichten).

Die heruntergeladene Datei enthält einen privaten Schlüssel und darf weder veröffentlicht noch in Git eingecheckt werden. Auf dem Linux-Docker-Host wird sie beispielsweise so abgelegt:

```bash
sudo install -d -o "$(id -u)" -g "$(id -g)" -m 700 \
  /opt/bg-offers-wireguard/wg_confs
install -m 600 /pfad/zur/heruntergeladenen-fritzbox.conf \
  /opt/bg-offers-wireguard/wg_confs/wg0.conf
```

In `wg0.conf` muss `AllowedIPs` den Wert `0.0.0.0/0` enthalten, damit der gesamte IPv4-Verkehr von `bg-offers` durch den Tunnel läuft. Falls die exportierte Datei zusätzlich `::/0` enthält, der Docker-Host oder Anschluss aber kein funktionierendes IPv6 unterstützt, sollte `::/0` entfernt werden.

Das folgende Beispiel startet zuerst einen WireGuard-Client und anschließend `bg-offers` in dessen Netzwerk-Namespace. Deshalb wird `8089:8080` am WireGuard-Container veröffentlicht und am Anwendungscontainer weder `-p` noch ein eigenes Docker-Netzwerk angegeben. `8089` ist der Port auf dem Host; die Anwendung lauscht im gemeinsamen Netzwerk-Namespace weiterhin auf Port `8080`.

```bash
docker rm -f bg-offers bg-offers-wireguard 2>/dev/null

docker run -d \
  --name bg-offers-wireguard \
  --pull=always \
  --cap-add=NET_ADMIN \
  --sysctl net.ipv4.conf.all.src_valid_mark=1 \
  -e PUID="$(id -u)" \
  -e PGID="$(id -g)" \
  -e TZ="Europe/Berlin" \
  -v /opt/bg-offers-wireguard:/config \
  -p 8089:8080 \
  --restart unless-stopped \
  lscr.io/linuxserver/wireguard:latest

docker run -d \
  --name bg-offers \
  --pull=always \
  --init \
  --network container:bg-offers-wireguard \
  -v "$(pwd)/data:/app/data" \
  -e INITIAL_IMPORT="false" \
  -e BGG_MARKET_ENABLED="true" \
  -e BGG_API_TOKEN="BGG_TOKEN" \
  -e TELEGRAM_BOT_TOKEN="BOT_TOKEN" \
  -e TELEGRAM_CHAT_ID="CHAT_ID" \
  -e UNKNOWNS_USERNAME="BENUTZERNAME_ODER_EMAIL" \
  -e UNKNOWNS_PASSWORD="PASSWORT" \
  --restart unless-stopped \
  ghcr.io/jensgiehl/bg-offers:latest
```

Auf aktuellen Linux-Kerneln ist das WireGuard-Modul üblicherweise bereits vorhanden. Meldet der WireGuard-Container ein fehlendes Kernelmodul, können zusätzlich `--cap-add=SYS_MODULE` und `-v /lib/modules:/lib/modules:ro` gesetzt werden. Weitere Details zum Client-Modus enthält die [Dokumentation des verwendeten WireGuard-Images](https://docs.linuxserver.io/images/docker-wireguard/).

Der Tunnel und die verwendete öffentliche Ausgangs-IP lassen sich anschließend prüfen:

```bash
docker exec bg-offers-wireguard wg show
docker run --rm --network container:bg-offers curlimages/curl:latest -fsS https://api.ipify.org
```

Die zweite Ausgabe sollte der öffentlichen IPv4-Adresse des heimischen FRITZ!Box-Anschlusses entsprechen. Wird der WireGuard-Container ersetzt oder neu gestartet, sollte anschließend auch `bg-offers` neu gestartet werden, damit der gemeinsam verwendete Netzwerk-Namespace sicher zum aktuellen Container gehört. Die Web-Oberfläche bleibt auf dem Docker-Host unter `http://localhost:8089` erreichbar.

Ein erfolgreicher Abruf über die heimische IPv6-Adresse bedeutet nicht automatisch, dass dieselbe Seite auch über die heimische IPv4-Adresse erreichbar ist. Bei HTTP 403 können `curl -4` und `curl -6` mit denselben Browser-Headern getrennt zur Diagnose verwendet werden. Funktioniert nur IPv6, reicht der in der FRITZ!Box integrierte WireGuard-Server derzeit nicht als Internet-Gateway: Laut [FRITZ!-Dokumentation](https://fritz.com/apps/knowledge-base/fritz-box-7632/3732_does-the-fritz-box-transmit-ipv6-data-over-vpn) kann er zwar eine VPN-Verbindung über IPv6 herstellen und entfernte Heimnetzgeräte per IPv6 zugänglich machen, routet aber keinen IPv6-Internetzugriff für den VPN-Client. `::/0` allein löst das daher nicht. Mögliche Alternativen sind der Betrieb von `bg-offers` im Heimnetz, eine neue beziehungsweise entsperrte öffentliche IPv4-Adresse oder ein eigener IPv6-fähiger VPN-Gateway beziehungsweise Proxy im Heimnetz.

Statt einer FRITZ!Box kann die WireGuard-Konfiguration eines VPN-Anbieters auf dieselbe Weise als `wg0.conf` verwendet werden. Der Anbieter muss einen vollständigen Tunnel erlauben, und dessen Ausgangs-IP darf von brettspiel-angebote.de nicht ebenfalls gesperrt sein. Der komplette ausgehende Verkehr von `bg-offers` – einschließlich Telegram, BoardGameGeek und aller weiteren Quellen – läuft in dieser Variante über das VPN.

## Tests

```bash
mvn test
```

Der echte Live-Test sucht „Scythe“ direkt auf brettspiel-angebote.de und erwartet einen positiven verfügbaren Preis. Er ist standardmäßig deaktiviert, damit ein externer Ausfall den normalen Build nicht fehlschlagen lässt:

```bash
RUN_LIVE_PRICE_COMPARISON_TEST=true mvn -Dtest=PriceComparisonLiveTest test
```

Unter PowerShell:

```powershell
$env:RUN_LIVE_PRICE_COMPARISON_TEST="true"
mvn -Dtest=PriceComparisonLiveTest test
Remove-Item Env:RUN_LIVE_PRICE_COMPARISON_TEST
```

Die Tests prüfen unter anderem alle vier Quellen, den rein lesenden Start-Systemcheck mit Erfolgs-, Leer- und Fehlerfällen, die BGG-Market-Feldzuordnung und Deduplizierung über `productid`, den direkten Einsatz von `objectid`, die Benachrichtigung nur bei einem günstigeren Market-Preis, den unknowns.de-Parser und dessen reine Titel-/Link-Meldungen, Gruppendeal-Mengen, Spieleschmiede-Filterung, Milan-Bildauswahl, HTTP- und Recherche-Wiederholungen, Namensnormalisierung, Bundle-Ausschluss, die Benachrichtigungsunterdrückung beim Initialimport, Telegram-Nachrichten ohne leere Werte, die einmaligen und erneut aktivierbaren Scraper-Health-Warnungen, die täglichen externen Health-Checks mit einmaliger Entwarnung nach einer Erholung, den vollständigen Preisvergleichsablauf aus Startseite, Suche und Weiterleitungsziel, die Cookie-Weitergabe einschließlich `bunny_shield*`, die Kontrolle der BoardGameGeek-ID, Vergleichspreise sowie die Darstellung des Activity Logs und der Übersicht fehlender Treffer.

## Hinweise zu externen Seiten

### HTTP 403 auf Windows und Raspberry Pi vergleichen

Die Diagnosewerkzeuge führen gezielte Abrufe aus. Die curl-Diagnose benötigt Python 3 und curl, aber keine zusätzlichen Python-Pakete. Sie verwendet die Browser-Header aus `scripts/price-comparison.curl`, passend zu den aktuellen Standardwerten des Java-Clients. `Accept-Encoding` entspricht den vom Apache-Client ergänzten Kompressionsformaten. Jeder Test lädt ausschließlich die Startseite mit einer frischen Verbindung, ohne Cookies, Redirect-Following oder Wiederholungen. Standardmäßig werden IPv6 mit HTTP/1.1, IPv6 mit HTTP/2 und IPv4 mit HTTP/1.1 verglichen. HTTP/2 wird übersprungen, wenn curl es nicht unterstützt; bei einer Aushandlung von HTTP/1.1 steht die tatsächlich verwendete Version im Bericht. Die Optionen sind in der [curl-Dokumentation](https://curl.se/docs/manpage.html) beschrieben.

Auf dem Raspberry Pi im Projektverzeichnis:

```bash
python3 scripts/diagnose-price-comparison.py
```

Unter Windows:

```powershell
python scripts/diagnose-price-comparison.py
```

Wenn die Änderungen noch ausschließlich im Windows-Workspace liegen, müssen zunächst die beiden Dateien `scripts/diagnose-price-comparison.py` und `scripts/price-comparison.curl` zusammen in das Verzeichnis `scripts/` des Pi-Checkouts übertragen werden. Für den folgenden Java-Test wird zusätzlich das neu gebaute JAR benötigt.

Der Bericht wird als UTF-8-JSON unter `diagnostics/` gespeichert; dieses Verzeichnis ist von Git ausgeschlossen. Er enthält curl-Version einschließlich TLS-Bibliothek, Java-Version, Adressfamilie, lokale und entfernte Verbindungsadresse, tatsächliche HTTP-Version, ausgewählte Antwort-Header einschließlich CDN-Request-ID sowie Größe und SHA-256 des empfangenen Antwortkörpers. Cookie-Werte und HTML-Inhalte werden nicht in den Bericht übernommen; die temporären Rohdateien werden nach jedem Test gelöscht. `wire_body_bytes` und `wire_body_sha256` beziehen sich auf den möglicherweise komprimierten Körper und sind deshalb nicht direkt mit dekomprimierten Java-Antwortgrößen vergleichbar. `local_ip` ist die lokale Socket-Adresse und kann bei IPv4 hinter NAT eine private Adresse sein. Die Diagnose deaktiviert die normale curl-Konfigurationsdatei und umgeht Umgebungs-Proxys ausdrücklich, damit ein direkter Netzwerktest entsteht.

Nur den bisher fehlenden IPv6-/HTTP/1.1-Test ausführen:

```bash
python3 scripts/diagnose-price-comparison.py --only ipv6-http1
```

Der gleiche Einzeltest ohne Python:

```bash
curl --disable --config scripts/price-comparison.curl \
  --noproxy '*' --ipv6 --http1.1 --silent --show-error \
  --connect-timeout 10 --max-time 30 --output /dev/null \
  --write-out 'HTTP=%{http_code} Version=%{http_version} Lokal=%{local_ip} Ziel=%{remote_ip}\n'
```

Bei weiterhin unterschiedlichen Ergebnissen kann die im Windows-Bericht beobachtete **entfernte** IPv6-Adresse auf dem Pi als Ziel festgelegt werden. Beim lokalen Test am 03.10.2026 war das `2400:52e0:1e00:2::1331:1`; CDN-Adressen können sich ändern, deshalb möglichst einen zeitnahen Windows-Bericht verwenden:

```bash
python3 scripts/diagnose-price-comparison.py \
  --only ipv6-http1 --remote-ip 2400:52e0:1e00:2::1331:1
```

`--remote-ip` verwendet curl `--resolve`; Hostname, Zertifikatsprüfung und TLS-SNI bleiben erhalten. Dies reduziert DNS-bedingte Zielunterschiede, garantiert bei einem CDN aber keinen identischen physischen Server. Mit `--source-ip` kann optional eine **bereits am jeweiligen Gerät konfigurierte** lokale Quelladresse ausgewählt werden. Eine Windows-Adresse darf dabei nicht einfach auf dem Pi verwendet werden. Das Skript filtert die Testvarianten passend zur Adressfamilie der vorgegebenen IP.

Ein zusätzlicher Vergleich mit ausschließlich TLS 1.2 ist möglich:

```bash
python3 scripts/diagnose-price-comparison.py --tls12
```

Für den Java-Vergleich gibt es einen Diagnosemodus im normalen ausführbaren JAR. Er startet keinen Spring-Kontext, Webserver, Scheduler, Datenbankzugriff oder Telegram-Versand. Er verwendet den bestehenden Dokument-Client samt Startseite, Cookie-Sitzung, Suche und Detailseite und sucht genau einmal nach „Scythe“. Automatische Wiederholungen sind deaktiviert. Die DNS-Ausgabe zeigt nur die Kandidatenreihenfolge. Für jede empfangene HTTP-Antwort zeigt eine zusätzliche Zeile `Verbindung:` die tatsächlichen lokalen und entfernten Socket-Adressen einschließlich Ports, HTTP-Status und Protokoll sowie TLS-Version, Cipher und CDN-Request-ID. Diese Daten stammen aus dem Apache-Verbindungskontext; Cookie-Werte werden dabei nicht ausgegeben. Bei einem Verbindungsfehler vor der HTTP-Antwort gibt es keine solche Zeile. Verwendet werden die im JAR enthaltene `application.yml` und deren Umgebungsvariablen; externe Spring-Konfigurationsdateien und zusätzliche Spring-Kommandozeilenoptionen werden in diesem Modus nicht eingelesen.

Unter Windows bauen und testen:

```powershell
mvn package
Get-FileHash target/bg-offers-0.0.1-SNAPSHOT.jar -Algorithm SHA256
java -jar target/bg-offers-0.0.1-SNAPSHOT.jar --diagnose-price-comparison
```

**Dasselbe gebaute JAR** auf den Pi kopieren und dort zunächst mit der vorhandenen Java-Version, anschließend mit Temurin 25 möglichst im gleichen Patchstand wie Windows ausführen. Die JAR-Hashes müssen übereinstimmen; auf dem Pi hierfür nicht erneut bauen:

```bash
sha256sum bg-offers-0.0.1-SNAPSHOT.jar
java -jar bg-offers-0.0.1-SNAPSHOT.jar --diagnose-price-comparison
/pfad/zu/temurin-25/bin/java -jar bg-offers-0.0.1-SNAPSHOT.jar --diagnose-price-comparison
```

Exit-Code `0` bedeutet einen gefundenen Preis, `2` einen erfolglosen Preisvergleich. Fehler vor dem Preisvergleich, beispielsweise bei der DNS-Auflösung, führen zu einem anderen Fehler-Exit-Code. `--jar /pfad/zum/bg-offers-0.0.1-SNAPSHOT.jar` ergänzt bei der Python-Diagnose den JAR-Hash im JSON-Bericht. Die dort ausgegebene Java-Version gehört zu `java` im `PATH`; bei Tests mit einem absoluten Java-Pfad ist die Java-Ausgabe des Diagnosemodus maßgeblich.

Am 03.10.2026 wurde im Windows-Workspace mit Temurin `25.0.3+9` beobachtet: Der bestehende Java-Live-Test war erfolgreich; curl/Schannel lieferte bei HTTP/1.1 über IPv6 HTTP 200 und über IPv4 HTTP 403. Der zusätzliche IPv6-Test mit TLS 1.2 lieferte ebenfalls HTTP 200. Windows-curl unterstützte kein HTTP/2. Diese Ergebnisse sind eine Vergleichsbasis und belegen keine Sperre der Pi-Adresse.

Im anschließenden Raspberry-Pi-Vergleich lieferte dieselbe curl-Konfiguration über HTTP/1.1 mit derselben IPv6-Zieladresse zunächst HTTP 403, mit einer zusätzlich eingerichteten zufälligen IPv6-Quelladresse aus demselben lokalen `/64` HTTP 200 und danach mit der ursprünglichen Quelladresse erneut HTTP 403. Damit ist die unterschiedliche Behandlung der konkreten Quelladressen in diesem Test belegt. Welche Schutzregel dafür verantwortlich ist, lässt sich daraus nicht bestimmen. Auf der ursprünglichen Pi-Adresse scheiterten auch Java mit Zulu 27 und Temurin 25 sowie automatisiertes Headless-Chromium bereits an der Startseite.

Für Pi-Systeme mit NetworkManager ist das Bevorzugen temporärer IPv6-Adressen eine mögliche dauerhafte Konfiguration: Die Einstellung `ipv6.ip6-privacy=2` erzeugt für SLAAC temporäre Adressen und bevorzugt sie für ausgehende Verbindungen. Sie wird im aktiven Verbindungsprofil gespeichert und betrifft auch andere Programme, die dessen IPv6-Verbindung verwenden. Die Einstellung ist von `ipv6.addr-gen-mode=stable-privacy` zu unterscheiden. Details beschreibt die [NetworkManager-Dokumentation](https://www.networkmanager.dev/docs/api/latest/settings-ipv6.html). Änderungen lassen sich mit `nmcli device reapply` auf die aktive Verbindung anwenden, soweit NetworkManager sie im laufenden Betrieb unterstützt. Vor der Annahme eines behobenen Java-Zugriffs müssen die tatsächlich ausgewählte Quelladresse mit `ip -6 route get` und anschließend der vollständige JAR-Diagnosemodus geprüft werden. Ein erfolgreicher Startseitenabruf mit curl allein bestätigt noch nicht die Suche und Preis-Auswertung mit Java. Bei einem anderen Netzwerkmanager muss dessen IPv6-Konfiguration verwendet werden; eine NetworkManager-Konfiguration darf nicht ungeprüft übertragen werden.

Beim untersuchten Pi war die Verbindung auf `wlan0` als Netplan-Profil vorhanden, hatte aber `ipv6.method=ignore`. Der vorgeschlagene NetworkManager-Block brach deshalb vor einer Änderung ab. Für dieses Profil darf die Anleitung für `ipv6.method=auto` nicht unverändert angewendet werden. Als Laufzeittest wurde stattdessen `net.ipv6.conf.wlan0.use_tempaddr=2` vorgeschlagen; dieser Kernel-Wert aktiviert die Privacy Extensions und bevorzugt temporäre Adressen. In der anschließenden Ausgabe war eine öffentliche temporäre IPv6-Adresse vorhanden, die `ip -6 route get` auch als Quelladresse für das geprüfte CDN-Ziel auswählte. Welche Adresse der vorherige Java-Aufruf verwendete, ist damit nicht rückwirkend belegt. Details zum Kernel-Wert enthält die [Linux-Dokumentation](https://docs.kernel.org/networking/ip-sysctl.html).

Der anschließende vollständige JAR-Test mit Zulu `27+35` auf dem Pi war erfolgreich: Der Apache-Client verwendete für alle vier Antworten dieselbe temporäre IPv6-Quelladresse und erhielt HTTP 200, 302, 200 und 200 über HTTP/1.1 mit TLS 1.3. Die Suche nach Scythe lieferte `FOUND` und einen verfügbaren Preis von 67,95 Euro. Zusammen mit dem curl-Vergleich bestätigt dies den Einfluss der Quelladresse und den erfolgreichen Java-Zugriff über eine temporäre Adresse. Die genaue Schutzregel des Betreibers bleibt unbekannt.

Für diesen Pi kann die erfolgreich getestete Kernel-Einstellung in einer eigenen Datei gespeichert werden. Als `root` ausführen:

```bash
cat > /etc/sysctl.d/99-bg-offers-ipv6-privacy.conf <<'EOF'
net.ipv6.conf.wlan0.use_tempaddr = 2
EOF
sysctl -p /etc/sysctl.d/99-bg-offers-ipv6-privacy.conf
```

Die Einstellung betrifft ausgehende IPv6-Verbindungen über `wlan0`, auch die anderer Programme. Die konkrete temporäre IP wird nicht festgeschrieben. Laut [systemd-Dokumentation zu sysctl.d](https://github.com/systemd/systemd/blob/main/man/sysctl.d.xml) werden diese Dateien beim Systemstart eingelesen; Einstellungen für Netzwerkschnittstellen werden auch beim Erscheinen der Schnittstelle angewendet. Der laufende Anwendungsprozess sollte anschließend neu gestartet werden, damit neue Verbindungen aufgebaut werden. Nach dem nächsten Neustart des Pi im Projektverzeichnis prüfen:

```bash
sysctl net.ipv6.conf.wlan0.use_tempaddr
ip -6 address show dev wlan0 scope global
ip -6 route get 2400:52e0:1e00:2::1332:1
java -jar target/bg-offers-0.0.1-SNAPSHOT.jar --diagnose-price-comparison
```

Erwartet werden `use_tempaddr = 2`, eine öffentliche Adresse mit `temporary`, eine temporäre Quelladresse in der Route und `FOUND` beim Java-Test. Der Test nach einem Neustart ist für diesen Pi noch offen; dabei wird auch geprüft, ob eine andere Netzwerkkonfiguration den Kernel-Wert überschreibt.

| Neuer Befund auf dem Pi | Aussage und nächster Versuch |
| --- | --- |
| HTTP/1.1 erfolgreich, HTTP/2 mit 403 | Die Protokollvarianten werden unterschiedlich behandelt. Der Java-Client verwendet bereits HTTP/1.1; den JAR-Test separat vergleichen. |
| Beide HTTP-Versionen mit 403 | HTTP/2 allein erklärt den Fehler nicht. Dieselbe Ziel-IP und dasselbe JAR mit Temurin 25 vergleichen. |
| Temurin 25 erfolgreich, bisheriges Java mit Fehler | JVM-/TLS-Unterschiede sind ein Kandidat; bei identischen Bedingungen wiederholen. |
| Chromium erfolgreich, curl und Java mit 403 | Browser-spezifisches Verhalten ist ein Kandidat. Das belegt noch keine einzelne TLS- oder JavaScript-Ursache; einen automatisierten Browserzugriff separat testen. |
| Auch Chromium mit 403 | Mit Zeitpunkt, CDN-Request-ID und Vergleichsergebnissen den Betreiber um Prüfung oder offiziellen Datenzugang bitten. |

### Zugriffspfad und Protokollierung

Die Anwendung wertet die HTML-Detailseiten von brettspiel-angebote.de aus. Dafür gibt es genau eine Implementierung von `PriceComparisonClient`; sie kapselt den einzigen Zugriffspfad und verwendet Springs `RestClient` mit Apache HttpClient, konsistenten Browser- und Fetch-Headern sowie einem gemeinsamen Cookie-Speicher. Ein eigener DNS-Resolver bevorzugt für diesen Client IPv6 und behält IPv4 als Fallback. Das ist wichtig, weil der vorgeschaltete Schutzdienst einen Zugriff über IPv4 mit HTTP 403 ablehnen kann, während derselbe Aufruf über IPv6 funktioniert. Die übrigen externen Clients der Anwendung werden von dieser Präferenz nicht beeinflusst. Der allgemeine `HttpDocumentClient` ist nicht Teil dieses Preisvergleichspfads und lehnt Aufrufe an den konfigurierten Preisvergleichs-Ursprung ausdrücklich ab. Zuerst wird die Startseite geladen, danach `/suche/?s=Suchbegriff` ohne automatische Weiterleitung aufgerufen und anschließend die URL aus dem `Location`-Header über HTTP/1.1 geladen. Handelt es sich dabei um eine Suchergebnisliste, wählt der Client den Eintrag mit der angeforderten BoardGameGeek-ID und lädt dessen Detailseite. Weiterleitungen und Treffer auf einem anderen Ursprung werden abgelehnt. `If-Modified-Since` wird bewusst nicht gesendet, damit der Client keine leere `304 Not Modified`-Antwort erhält. Jsoup verarbeitet anschließend ausschließlich die geladenen Inhalte und führt kein JavaScript aus. Verlangt ein vorgeschalteter Schutzdienst dennoch eine JavaScript-Prüfung, wird der Abruf als technischer Fehler protokolliert. Ändern die Betreiber Markup, Endpunkte oder Schutzmechanismen, können ebenfalls einzelne Abrufe fehlschlagen. Für brettspiel-angebote.de und BoardGameGeek gibt es zusätzlich tägliche Prüfungen mit Telegram-Warnung. Betreiberregeln und zulässige Abruffrequenzen sollten beim produktiven Einsatz beachtet werden.

Vom betroffenen Rechner lässt sich die unterschiedliche Behandlung der Adressfamilien mit `curl -4` und `curl -6` prüfen. Liefert nur der IPv6-Aufruf HTTP 200, muss das Betriebssystem beziehungsweise das Container-Netzwerk über eine funktionsfähige öffentliche IPv6-Verbindung verfügen; die Anwendung kann fehlende IPv6-Konnektivität nicht durch die DNS-Sortierung ersetzen.

Alle ausschließlich zum Preisvergleich gehörenden Bestandteile sind im Modul-Package `de.agiehl.bgoffers.pricecomparison` gebündelt: Service und Ergebnisobjekt, Client-Schnittstelle, Dokument-Client und interner HTTP-Client. Gemeinsam genutzte Bausteine wie die Namensnormalisierung bleiben in ihren bisherigen Packages. Die Logger behalten jeweils den vollständigen Klassennamen; dank des gemeinsamen Package-Präfixes lässt sich ihr Log-Level trotzdem zusammen steuern.

Die detaillierten HTTP-Schritte des Preisvergleichs werden auf `DEBUG`-Ebene protokolliert. Dafür kann vorübergehend `PRICE_COMPARISON_LOG_LEVEL=DEBUG` gesetzt werden. Alternativ kann das Spring-Boot-Property `logging.level.de.agiehl.bgoffers.pricecomparison` verwendet werden. Ein Fehler bleibt auch auf `WARN` sichtbar und nennt unter anderem `Typ=Startseite`, `Typ=Suche` oder `Typ=Detailseite`. Das Debug-Log enthält vollständige Cookie-Werte, einschließlich `bunny_shield*`, und muss deshalb wie ein Geheimnis behandelt, nur kurzfristig aktiviert und vor einer Weitergabe bereinigt werden.
