# BG Offers

BG Offers sammelt Brettspielangebote von Spiele-Offensive, Milan-Spiele und dem BGG Market sowie neue Themen aus dem Schnäppchenforum von unknowns.de. Shop- und Market-Angebote werden mit BoardGameGeek- und Vergleichspreisdaten angereichert; unknowns.de-Themen werden ausschließlich mit Titel und Link gespeichert und gemeldet. Ein dauerhaftes Activity Log macht diese Abläufe auch in der Web-Oberfläche nachvollziehbar. Ohne Telegram-Konfiguration werden dieselben Meldungen im Anwendungslog ausgegeben.

Die Anwendung verwendet Java 25, Spring Boot, Maven, H2 mit Flyway, Jsoup, Thymeleaf und Bootstrap als WebJar. Die Web-Oberfläche ist ausschließlich lesend und unter `http://localhost:8080` erreichbar.

## Funktionsumfang

- Liest die Banner innerhalb von `#wrapper_startseite` auf Spiele-Offensive aus.
- Lädt für normale Angebote und Gruppendeals die Detailseite, um Name, Preis, Verfügbarkeit und die Gruppendeal-Restmenge zu bestimmen. Bei Gruppendeals wird der tatsächlich an den Warenkorb übergebene Preis verwendet, sodass Trostangebote und optionale VIP-Upgrades nicht als Dealpreis erkannt werden.
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
- Wählt bei mehreren Preisvergleichstreffern bevorzugt das Spiel mit derselben BoardGameGeek-ID.
- Speichert neue Angebote und Preisänderungen mit einer kompakten Vorschau sowie dem damaligen Suchstatus bei brettspiel-angebote.de und BoardGameGeek im Activity Log.
- Wiederholt technische Recherchefehler mit einer Pause, hält jeden erneuten Versuch im Activity Log fest und benachrichtigt erst nach abgeschlossener Recherche oder dem letzten Versuch.
- Protokolliert erfolgreiche und fehlgeschlagene Telegram-Sendeversuche ohne Nachrichteninhalt, Bot-Token oder Chat-ID.
- Lässt nicht verfügbare Werte wie Verfügbarkeit, Vergleichspreis, Bestpreis oder BGG-Daten in Telegram-Nachrichten vollständig weg.
- Meldet neue oder preislich veränderte Angebote, wenn sie günstiger als ein aktuell verfügbares Vergleichsangebot sind oder eine der Zusatzquellen keinen Treffer liefert.
- Verhindert mit einem Fingerabdruck aus Quelle, URL und Preis doppelte Meldungen.
- Aktualisiert alle Quellen alle fünf Minuten.
- Überwacht, wann pro aktiver Quelle zuletzt ein neuer Datensatz gespeichert wurde. Nach vier Tagen ohne neue Daten von Spiele-Offensive, Milan-Spiele oder dem BGG Market beziehungsweise nach 30 Tagen bei unknowns.de wird genau eine Warnung gesendet. Ein späterer neuer Datensatz aktiviert die Warnung für die nächste Ruhephase erneut.
- Verwendet die Schnellsuche von brettspiel-angebote.de und verkürzt unbekannte Editionsnamen schrittweise, ohne eine abweichende Edition als Treffer zu übernehmen.
- Prüft täglich um 08:00 Uhr Europe/Berlin mit „Scythe“, ob brettspiel-angebote.de weiterhin auswertbar ist.
- Prüft im selben täglichen Lauf mit „Magical Athlete“, ob Daten von BoardGameGeek abgefragt werden können.

## Voraussetzungen

- JDK 25
- Maven 3.9 oder neuer
- Optional: Telegram-Bot und Chat-ID
- Optional, aber für BGG-Daten erforderlich: persönlicher BGG-API-Token

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
| `INITIAL_IMPORT` | Importiert und speichert Angebote ohne Benachrichtigungen | `false` |
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

Die Quellen lassen sich außerdem direkt mit den Spring-Properties `offers.sources.spiele-offensive-enabled`, `offers.sources.milan-enabled`, `offers.sources.bgg-market-enabled` und `offers.sources.unknowns-enabled` einzeln ein- oder ausschalten. Alle vier sind standardmäßig aktiviert. Weitere Einstellungen wie Quell-URLs, Zeitpläne, HTTP-Timeout, Wiederholungsversuche und Parallelität der Milan-Detailabrufe befinden sich in `src/main/resources/application.yml` und können über die üblichen Spring-Boot-Konfigurationsmechanismen überschrieben werden. Standardmäßig werden temporäre HTTP- und Recherchefehler bis zu dreimal mit 750 Millisekunden Pause versucht und höchstens zwei Milan-Detailseiten gleichzeitig geladen.

Das Schnäppchenforum von unknowns.de ist derzeit nur für angemeldete Benutzer erreichbar. Vor jedem Abruf meldet sich die Anwendung mit `UNKNOWNS_USERNAME` und `UNKNOWNS_PASSWORD` über das Login-Formular an. Die dabei gesetzten Session-Cookies werden ausschließlich im Arbeitsspeicher verwaltet und automatisch beim anschließenden Forenabruf mitgesendet. Die Zugangsdaten gehören nicht in die Versionsverwaltung oder in Logs. Wird die Quelle nicht benötigt, kann sie mit `UNKNOWNS_ENABLED=false` deaktiviert werden.

Mit `INITIAL_IMPORT=true` werden Angebote weiterhin vollständig importiert, angereichert und im Activity Log erfasst, aber nicht gemeldet. Nach dem initialen Befüllen sollte die Variable wieder auf `false` gesetzt werden. Bereits importierte, unveränderte Angebote werden anschließend nicht nachträglich gemeldet; Benachrichtigungen beginnen mit neuen Angeboten oder Preisänderungen.

## Web-Oberfläche

Die Angebotsübersicht ist unter `http://localhost:8080/` erreichbar. Das paginierte Activity Log befindet sich unter `http://localhost:8080/aktivitaeten` und ist direkt über die Hauptnavigation verlinkt. Es zeigt für Angebotsereignisse Bild, Name, Preisentwicklung, Quelle, Recherche-Retries sowie die Ergebnisse von brettspiel-angebote.de und BoardGameGeek. Telegram-Einträge enthalten ausschließlich Zeitpunkt und Zustellstatus.

Unter `http://localhost:8080/fehlende-treffer` stehen zwei getrennte Listen der bereinigten Suchbegriffe, für die BoardGameGeek beziehungsweise brettspiel-angebote.de keinen Treffer geliefert hat. Technische Fehler, nicht konfigurierte Quellen und übersprungene Bundle-Angebote werden dort nicht als fehlende Treffer gezählt.

Übersprungene Recherchen werden in der Oberfläche nicht mit dem Status „Nicht erforderlich“ dargestellt; der jeweilige Bereich bleibt stattdessen ausgeblendet.

BGG stellt API-Zugriffe nur mit einem gültigen Token bereit. Das verwendete [bggClient-Projekt](https://github.com/jensGiehl/bggClient) beschreibt die Einbindung des Tokens.

## Telegram-Verhalten

Normale Meldungen enthalten kompakt Name, Angebotspreis, Verfügbarkeit, verfügbaren Vergleichspreis, historischen Bestpreis, BGG-Bewertung, „Want to buy“, „Want in trade“ sowie klickbare Links. Wenn ein Angebotsbild vorhanden ist, wird es direkt als Telegram-Foto mit Beschriftung gesendet.

Eine Meldung gilt erst dann als versendet, wenn Telegram den Aufruf erfolgreich bestätigt hat. Fehlerhafte Sendeversuche werden deshalb beim nächsten relevanten Lauf erneut versucht. Ohne Telegram-Konfiguration gilt die Ausgabe im Log als erfolgreiche lokale Meldung.

Die täglichen Health-Checks melden fehlgeschlagene Zugriffe auf brettspiel-angebote.de und BoardGameGeek. Die Ruhezeit-Überwachung berücksichtigt nur aktivierte Scraper und wertet einen erstmals gespeicherten Eintrag als neue Daten. Ihr Alarmzustand liegt dauerhaft in der Datenbank: Während derselben Ruhephase wird nur einmal gewarnt, nach einem neuen Datensatz kann eine spätere Ruhephase erneut eine Warnung auslösen.

## Docker

Das Image wird einschließlich Tests gebaut:

```bash
docker build -t bg-offers:local .
```

Für einen Betrieb mit dem in der GitHub Container Registry veröffentlichten Image kann das folgende Skript verwendet werden. `8089` ist dabei der Port auf dem Host; innerhalb des Containers läuft die Anwendung auf Port `8080`. Das Verzeichnis `./data` wird eingebunden, damit die H2-Datenbank beim Ersetzen des Containers erhalten bleibt.

```bash
docker rm -f bg-offers 2>/dev/null

docker run -d \
  --name bg-offers \
  --pull=always \
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

## Tests

```bash
mvn test
```

Die Tests prüfen unter anderem alle vier Quellen, die BGG-Market-Feldzuordnung und Deduplizierung über `productid`, den direkten Einsatz von `objectid`, die Benachrichtigung nur bei einem günstigeren Market-Preis, den unknowns.de-Parser und dessen reine Titel-/Link-Meldungen, Gruppendeal-Mengen, Spieleschmiede-Filterung, Milan-Bildauswahl, HTTP- und Recherche-Wiederholungen, Namensnormalisierung, Bundle-Ausschluss, die Benachrichtigungsunterdrückung beim Initialimport, Telegram-Nachrichten ohne leere Werte, die einmaligen und erneut aktivierbaren Scraper-Health-Warnungen, die täglichen externen Health-Checks, den vollständigen Suchablauf über die Schnellsuche, die Verkürzung unbekannter Editionsnamen, die Auswahl aus mehreren Scythe-Treffern anhand der BoardGameGeek-ID, Vergleichspreise sowie die Darstellung des Activity Logs und der Übersicht fehlender Treffer.

## Hinweise zu externen Seiten

Die Anwendung wertet HTML-Seiten und die öffentlich von der Seitensuche verwendete JSON-Antwort aus. Ändern die Betreiber Markup, Endpunkte oder Schutzmechanismen, können einzelne Abrufe fehlschlagen. Solche Fehler werden protokolliert; für brettspiel-angebote.de und BoardGameGeek gibt es zusätzlich tägliche Prüfungen mit Telegram-Warnung. Betreiberregeln und zulässige Abruffrequenzen sollten beim produktiven Einsatz beachtet werden.
