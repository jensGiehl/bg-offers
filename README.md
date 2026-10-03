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
- Für den Preisvergleich: funktionierender Internetzugang mit einer von brettspiel-angebote.de akzeptierten Quelladresse. Für den Raspberry Pi ist der Zugriff über temporäre öffentliche IPv6-Adressen bestätigt; die Docker-Konfiguration übernimmt dafür das Host-Netzwerk.

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

Das Image unterstützt `linux/amd64` und `linux/arm64`, einschließlich 64-Bit-Raspberry-Pi-Systemen. Es läuft mit Java 25 und als Benutzer `app` mit UID/GID `10001`. Der GitHub-Workflow baut beide Architekturen einschließlich der Tests und veröffentlicht sie unter `ghcr.io/jensgiehl/bg-offers:latest`. Anschließend prüft er für beide Architekturen den Containerstart mit Host-Netzwerk auf Port 8089, die Web-Oberfläche und das Anlegen der Datenbank in einem eingebundenen Ordner; externe Abrufe sind für diese Prüfung deaktiviert.

### Raspberry Pi mit funktionierendem IPv6-Zugriff

Auf dem untersuchten Pi funktioniert der vollständige Preisvergleich über eine temporäre öffentliche IPv6-Adresse. Der Container verwendet deshalb das **Host-Netzwerk** des Linux-Pi. Damit stehen dieselben IPv6-Adressen, Routen und die auf dem Host konfigurierte Quelladresswahl zur Verfügung. Ein gewöhnliches Docker-Bridge-Netzwerk übernimmt diese Einstellung nicht automatisch. Die [Docker-Dokumentation zum Host-Netzwerk](https://docs.docker.com/engine/network/drivers/host/) beschreibt diesen Modus.

Die erfolgreiche Einstellung bleibt auf dem **Pi-Host** in `/etc/sysctl.d/99-bg-offers-ipv6-privacy.conf` gespeichert:

```ini
net.ipv6.conf.wlan0.use_tempaddr = 2
```

Der Container benötigt dafür keine zusätzlichen Rechte und keine eigenen Netzwerk-Sysctls. Die konkrete temporäre IP wird nicht festgeschrieben. Die Einstellung gilt für `wlan0`; bei einer anderen Netzwerkschnittstelle muss deren Name verwendet werden. Details zu temporären Adressen beschreibt die [Linux-Dokumentation](https://docs.kernel.org/networking/ip-sysctl.html).

Im Projektverzeichnis die Konfiguration und den Datenbankordner vorbereiten:

```bash
if [ ! -f .env ]; then cp .env.example .env; fi
chmod 600 .env
mkdir -p data
sudo chown -R 10001:10001 data
```

In `.env` bei Bedarf `BGG_API_TOKEN`, `TELEGRAM_BOT_TOKEN` und `TELEGRAM_CHAT_ID` eintragen. unknowns.de bleibt in der Vorlage deaktiviert; zum Aktivieren `UNKNOWNS_ENABLED=true` sowie Benutzername und Passwort setzen. `.env` wird von Git und vom Docker-Build ausgeschlossen. Der Container bindet `./data` unter `/app/data` ein, sodass die H2-Datenbank beim Ersetzen des Containers erhalten bleibt. Liegen die bisherigen Daten in einem anderen Verzeichnis, muss der Bind-Mount entsprechend angepasst werden. Eine vorhandene Java-Instanz vor dem Containerstart beenden, damit die Datenbank nur von einem Prozess geöffnet wird.

Mit Docker Compose v2 oder neuer starten:

```bash
docker compose pull
docker rm -f bg-offers 2>/dev/null
docker compose up -d
docker compose logs --tail=100 -f bg-offers
```

Die Compose-Datei verwendet `network_mode: host`, `restart: unless-stopped` und standardmäßig `SERVER_PORT=8089`. **8089 ist der Port am Host.** Da der Container das Host-Netzwerk verwendet, lauscht die Anwendung selbst auf Port 8089; es gibt hier keine Portweiterleitung. Port 8089 muss frei sein. Die Oberfläche ist im Heimnetz unter `http://<PI-IP>:8089` erreichbar. Der beim Start ausgeführte Systemcheck prüft auch den vollständigen Scythe-Preisvergleich; dessen Ergebnis erscheint in der Statusmeldung beziehungsweise ohne Telegram-Konfiguration im Anwendungslog.

Alternativ derselbe Start ohne Compose, mit der vorbereiteten `.env`:

```bash
docker rm -f bg-offers 2>/dev/null

docker run -d \
  --name bg-offers \
  --pull=always \
  --init \
  --restart unless-stopped \
  --network host \
  --env-file .env \
  -e SERVER_PORT=8089 \
  -e DB_PATH=/app/data/bg-offers \
  --mount "type=bind,source=$(pwd)/data,target=/app/data" \
  ghcr.io/jensgiehl/bg-offers:latest
```

Bei Updates `docker compose pull` und anschließend `docker compose up -d` ausführen. Nach einem Host-Neustart läuft der Container automatisch wieder an, sofern der Docker-Dienst beim Systemstart aktiviert ist. Für diesen Zugriffspfad wird Docker Engine auf Linux mit normalem Host-Netzwerk vorausgesetzt. Docker Desktop und Rootless-Docker verwenden andere Netzwerktechnik; dort muss der tatsächliche IPv6-Ausgang separat geprüft werden.

### Image lokal bauen

```bash
docker build \
  --build-arg GIT_COMMIT="$(git rev-parse HEAD)" \
  -t bg-offers:local .
```

Für den lokalen Start im obigen `docker run` die letzte Zeile durch `bg-offers:local` ersetzen und `--pull=always` entfernen. Das lokale Image wird ebenfalls einschließlich Tests gebaut.

### Betrieb mit einem eigenen Docker-Netzwerk

Wenn ein Docker-Netzwerk bereits einen geeigneten IPv6-Internetzugang besitzt, kann weiterhin ein klassischer Start mit Portweiterleitung verwendet werden. Das folgende Beispiel setzt diesen Zugang voraus und ist nicht die empfohlene Pi-Konfiguration:

```bash
docker rm -f bg-offers 2>/dev/null

docker run -d \
  --name bg-offers \
  --pull=always \
  --init \
  --restart unless-stopped \
  -p 8089:8080 \
  --env-file .env \
  -e SERVER_PORT=8080 \
  -e DB_PATH=/app/data/bg-offers \
  --mount "type=bind,source=$(pwd)/data,target=/app/data" \
  ghcr.io/jensgiehl/bg-offers:latest
```

Hier ist **8089 der Port am Host**, während die Anwendung im Container auf 8080 läuft. Docker-Bridge-Netzwerke benötigen eine passende IPv6-Konfiguration und Route; die bloße IPv6-Verfügbarkeit auf dem Host reicht nicht aus. Wird ein eigenes Netzwerk benötigt, kann es über `--network NETZWERKNAME` angegeben werden. Details beschreibt die [Docker-Dokumentation zu Bridge-Netzwerken](https://docs.docker.com/engine/network/drivers/bridge/).

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

### IPv6-Quelladresse und HTTP 403

Auf dem Raspberry Pi zeigte ein Vergleich mit identischen curl-Anfragen und derselben Zieladresse: ursprüngliche IPv6-Quelladresse HTTP 403, zusätzliche zufällige IPv6-Quelladresse aus demselben `/64` HTTP 200, ursprüngliche Adresse erneut HTTP 403. Nach dem Aktivieren und Bevorzugen temporärer IPv6-Adressen war auch der vollständige Java-Preisvergleich mit Zulu `27+35` erfolgreich. Die vier Antworten lieferten HTTP 200, 302, 200 und 200; Scythe wurde mit verfügbarem Preis gefunden. Der Benutzer bestätigte anschließend auch den Erfolg nach dem Speichern der Einstellung.

Damit ist der Einfluss der Quelladresse für diese Versuche belegt. Welche Schutzregel auf der Seite dafür verantwortlich war, bleibt unbekannt. Für den Docker-Betrieb auf diesem Pi wird die funktionierende Host-Konfiguration wie oben beschrieben verwendet.

### Zugriffspfad und Protokollierung

Die Anwendung wertet die HTML-Detailseiten von brettspiel-angebote.de aus. Dafür gibt es genau eine Implementierung von `PriceComparisonClient`; sie kapselt den einzigen Zugriffspfad und verwendet Springs `RestClient` mit Apache HttpClient, konsistenten Browser- und Fetch-Headern sowie einem gemeinsamen Cookie-Speicher. Ein eigener DNS-Resolver bevorzugt für diesen Client IPv6 und behält IPv4 als Fallback. Das ist wichtig, weil der vorgeschaltete Schutzdienst einen Zugriff über IPv4 mit HTTP 403 ablehnen kann, während derselbe Aufruf über IPv6 funktioniert. Die übrigen externen Clients der Anwendung werden von dieser Präferenz nicht beeinflusst. Der allgemeine `HttpDocumentClient` ist nicht Teil dieses Preisvergleichspfads und lehnt Aufrufe an den konfigurierten Preisvergleichs-Ursprung ausdrücklich ab. Zuerst wird die Startseite geladen, danach `/suche/?s=Suchbegriff` ohne automatische Weiterleitung aufgerufen und anschließend die URL aus dem `Location`-Header über HTTP/1.1 geladen. Handelt es sich dabei um eine Suchergebnisliste, wählt der Client den Eintrag mit der angeforderten BoardGameGeek-ID und lädt dessen Detailseite. Weiterleitungen und Treffer auf einem anderen Ursprung werden abgelehnt. `If-Modified-Since` wird bewusst nicht gesendet, damit der Client keine leere `304 Not Modified`-Antwort erhält. Jsoup verarbeitet anschließend ausschließlich die geladenen Inhalte und führt kein JavaScript aus. Verlangt ein vorgeschalteter Schutzdienst dennoch eine JavaScript-Prüfung, wird der Abruf als technischer Fehler protokolliert. Ändern die Betreiber Markup, Endpunkte oder Schutzmechanismen, können ebenfalls einzelne Abrufe fehlschlagen. Für brettspiel-angebote.de und BoardGameGeek gibt es zusätzlich tägliche Prüfungen mit Telegram-Warnung. Betreiberregeln und zulässige Abruffrequenzen sollten beim produktiven Einsatz beachtet werden.

Vom betroffenen Rechner lässt sich die unterschiedliche Behandlung der Adressfamilien mit `curl -4` und `curl -6` prüfen. Liefert nur der IPv6-Aufruf HTTP 200, muss das Betriebssystem beziehungsweise das Container-Netzwerk über eine funktionsfähige öffentliche IPv6-Verbindung verfügen; die Anwendung kann fehlende IPv6-Konnektivität nicht durch die DNS-Sortierung ersetzen.

Alle ausschließlich zum Preisvergleich gehörenden Bestandteile sind im Modul-Package `de.agiehl.bgoffers.pricecomparison` gebündelt: Service und Ergebnisobjekt, Client-Schnittstelle, Dokument-Client und interner HTTP-Client. Gemeinsam genutzte Bausteine wie die Namensnormalisierung bleiben in ihren bisherigen Packages. Die Logger behalten jeweils den vollständigen Klassennamen; dank des gemeinsamen Package-Präfixes lässt sich ihr Log-Level trotzdem zusammen steuern.

Die detaillierten HTTP-Schritte des Preisvergleichs werden auf `DEBUG`-Ebene protokolliert. Dafür kann vorübergehend `PRICE_COMPARISON_LOG_LEVEL=DEBUG` gesetzt werden. Alternativ kann das Spring-Boot-Property `logging.level.de.agiehl.bgoffers.pricecomparison` verwendet werden. Ein Fehler bleibt auch auf `WARN` sichtbar und nennt unter anderem `Typ=Startseite`, `Typ=Suche` oder `Typ=Detailseite`. Das Debug-Log enthält vollständige Cookie-Werte, einschließlich `bunny_shield*`, und muss deshalb wie ein Geheimnis behandelt, nur kurzfristig aktiviert und vor einer Weitergabe bereinigt werden.
