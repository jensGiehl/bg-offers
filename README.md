# BG Offers

BG Offers sammelt Brettspielangebote von Spiele-Offensive und Milan-Spiele sowie neue Themen aus dem Schnäppchenforum von unknowns.de. Shopangebote werden mit BoardGameGeek- und Vergleichspreisdaten angereichert; unknowns.de-Themen werden ausschließlich mit Titel und Link gespeichert und gemeldet. Ein dauerhaftes Activity Log macht diese Abläufe auch in der Web-Oberfläche nachvollziehbar. Ohne Telegram-Konfiguration werden dieselben Meldungen im Anwendungslog ausgegeben.

Die Anwendung verwendet Java 25, Spring Boot, Maven, H2, Jsoup, Thymeleaf und Bootstrap als WebJar. Die Web-Oberfläche ist ausschließlich lesend und unter `http://localhost:8080` erreichbar.

## Funktionsumfang

- Liest die Banner innerhalb von `#wrapper_startseite` auf Spiele-Offensive aus.
- Lädt für normale Angebote und Gruppendeals die Detailseite, um Name, Preis, Verfügbarkeit und die Gruppendeal-Restmenge zu bestimmen. Bei Gruppendeals wird der tatsächlich an den Warenkorb übergebene Preis verwendet, sodass Trostangebote und optionale VIP-Upgrades nicht als Dealpreis erkannt werden.
- Berücksichtigt den vom Server gemeldeten HTML-Zeichensatz, damit deutsche Umlaute korrekt übernommen werden.
- Verwendet bei dynamisch aufgebauten Spiele-Offensive-Bannern das eingebettete Produktbild statt der dekorativen `display_angebot`-Ebene.
- Behandelt Spieleschmiede-Banner ohne BGG- und Preisvergleich und versendet bei konfiguriertem Telegram nur das Bild.
- Ignoriert Inspirations-TV.
- Liest alle Angebotsseiten von Milan-Spiele ein und lädt das große Produktbild von jeder Detailseite. Temporäre HTTP-Fehler werden automatisch erneut versucht; die Detailabrufe sind gedrosselt.
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
- Verwendet die Schnellsuche von brettspiel-angebote.de und verkürzt unbekannte Editionsnamen schrittweise, ohne eine abweichende Edition als Treffer zu übernehmen.
- Prüft täglich um 08:00 Uhr Europe/Berlin mit „Scythe“, ob brettspiel-angebote.de weiterhin auswertbar ist.

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

Der erste Abruf beginnt 15 Sekunden nach dem Start. Die H2-Dateien werden standardmäßig im Verzeichnis `./data` abgelegt. Die Datenbank wird beim ersten Start automatisch erstellt und bei späteren Starts weiterverwendet.

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
| `UNKNOWNS_ENABLED` | Abruf des unknowns.de-Schnäppchenforums aktivieren | `true` |
| `UNKNOWNS_USERNAME` | Benutzername oder E-Mail-Adresse für unknowns.de | leer |
| `UNKNOWNS_PASSWORD` | Passwort für unknowns.de | leer |
| `DB_PATH` | Pfad der H2-Datenbank ohne Dateiendung | `./data/bg-offers` |
| `DB_USER` | H2-Benutzer | `sa` |
| `DB_PASSWORD` | H2-Passwort | leer |
| `SERVER_PORT` | HTTP-Port der Anwendung | `8080` |

Die Quellen lassen sich außerdem direkt mit den Spring-Properties `offers.sources.spiele-offensive-enabled`, `offers.sources.milan-enabled` und `offers.sources.unknowns-enabled` einzeln ein- oder ausschalten. Alle drei sind standardmäßig aktiviert. Weitere Einstellungen wie Quell-URLs, Zeitpläne, HTTP-Timeout, Wiederholungsversuche und Parallelität der Milan-Detailabrufe befinden sich in `src/main/resources/application.yml` und können über die üblichen Spring-Boot-Konfigurationsmechanismen überschrieben werden. Standardmäßig werden temporäre HTTP- und Recherchefehler bis zu dreimal mit 750 Millisekunden Pause versucht und höchstens zwei Milan-Detailseiten gleichzeitig geladen.

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

Die Tests prüfen unter anderem alle drei Quellen, den unknowns.de-Parser und dessen reine Titel-/Link-Meldungen, Gruppendeal-Mengen, Spieleschmiede-Filterung, Milan-Bildauswahl, HTTP- und Recherche-Wiederholungen, Namensnormalisierung, Bundle-Ausschluss, die Benachrichtigungsunterdrückung beim Initialimport, Telegram-Nachrichten ohne leere Werte, den vollständigen Suchablauf über die Schnellsuche, die Verkürzung unbekannter Editionsnamen, die Auswahl aus mehreren Scythe-Treffern anhand der BoardGameGeek-ID, Vergleichspreise sowie die Darstellung des Activity Logs und der Übersicht fehlender Treffer.

## Hinweise zu externen Seiten

Die Anwendung wertet HTML-Seiten und die öffentlich von der Seitensuche verwendete JSON-Antwort aus. Ändern die Betreiber Markup, Endpunkte oder Schutzmechanismen, können einzelne Abrufe fehlschlagen. Solche Fehler werden protokolliert; für brettspiel-angebote.de gibt es zusätzlich die tägliche „Scythe“-Prüfung mit Telegram-Warnung. Betreiberregeln und zulässige Abruffrequenzen sollten beim produktiven Einsatz beachtet werden.
