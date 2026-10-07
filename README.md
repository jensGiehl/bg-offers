# BG Offers

BG Offers sammelt Brettspielangebote von Spiele-Offensive, Milan-Spiele und dem BGG Market sowie neue Themen aus dem Schnäppchenforum von unknowns.de. Shop- und Market-Angebote werden mit BoardGameGeek- und Vergleichspreisdaten angereichert; unknowns.de-Themen werden mit Titel, Link und dem Forumslogo gespeichert und gemeldet. Ein dauerhaftes Activity Log macht diese Abläufe auch in der Web-Oberfläche nachvollziehbar. Ohne Telegram-Konfiguration werden dieselben Meldungen im Anwendungslog ausgegeben.

Die Vergleichspreise kommen vom separaten Projekt [brettspielpreise](https://github.com/jensGiehl/brettspielpreise). Die mitgelieferte Docker-Compose-Konfiguration startet beide Services gemeinsam; die Einrichtung auf dem Raspberry Pi steht im Abschnitt [Docker](#docker).

Die Anwendung verwendet Java 27, Spring Boot, Maven, H2 mit Flyway, Jsoup, Thymeleaf und Bootstrap als WebJar. Die Web-Oberfläche ist ausschließlich lesend und unter `http://localhost:8080` erreichbar.

Im Footer der Angebotsübersicht stehen die Version als siebenstellige Git-Commit-ID (vollständig im Tooltip) und der Build-Zeitpunkt mit Datum, Uhrzeit und Berliner Zeitzone. Maven schreibt den tatsächlichen Build-Startzeitpunkt über `maven.build.timestamp` in das JAR, unabhängig vom festen Archiv-Zeitstempel für reproduzierbare Builds; er bleibt bei Neustarts unverändert. Alle Uhrzeiten in Aktivitäten, Angebotsdetails und Rechercheverläufen werden in `Europe/Berlin` dargestellt, mit automatischer Umstellung zwischen Sommer- und Winterzeit und unabhängig von der Zeitzone des Hosts oder Containers. Der Docker-Workflow setzt die Commit-ID automatisch. Bei einem lokalen Start `GIT_COMMIT` auf das Ergebnis von `git rev-parse HEAD` setzen. Fehlt die Commit-ID oder bei einem IDE-Start die Build-Information, erscheint für den jeweiligen Wert „unbekannt“.

## Abhängigkeitsstand

Die Abhängigkeiten wurden am 6. Oktober 2026 auf verfügbare stabile Versionen geprüft. Spring Boot 4.1.1 bleibt der stabile Parent; dessen Versions-Properties werden für neuere Bibliotheken gezielt überschrieben. Vorabversionen werden nicht verwendet. Quellcode und Dokumentation verwenden UTF-8.

| Komponente | Version |
|---|---|
| Java / Maven im Docker-Build | 27 / 3.10.0 |
| Spring Boot / Spring Framework | 4.1.1 / 7.0.9 |
| Jsoup / Apache Batik | 1.23.2 / 1.19 |
| Bootstrap / Bootstrap Icons (WebJars) | 5.3.8 / 1.13.1 |
| Thymeleaf / Java-Time-Erweiterung | 3.1.5.RELEASE / 3.0.4.RELEASE |
| H2 / Hibernate ORM / HikariCP / Flyway | 2.5.252 / 7.4.12.Final / 7.1.0 / 13.9.0 |
| Jackson 3 / Jackson 2 | 3.2.3 / 2.22.3 |
| Logback / Log4j / SLF4J / SnakeYAML | 1.6.5 / 2.26.1 / 2.0.20 / 2.7 |
| JUnit / Mockito / Byte Buddy / XMLUnit | 6.1.3 / 5.24.0 / 1.18.14 / 2.14.0 |
| Maven Compiler / Surefire und Failsafe | 3.16.0 / 3.6.0 |
| Maven Install und Deploy / Versions | 3.2.0 / 2.22.0 |

Auch die transitiven XML-, Parser- und Hilfsbibliotheken sowie die GitHub Actions sind aktualisiert. Ihre Versionen stehen zentral in `pom.xml` beziehungsweise `.github/workflows/docker.yml`. Der BGG-Client bleibt auf `v1.0.0-1`: Der neuere Tag `v1.0.0-2` liefert derzeit keine JitPack-Artefakte, weil der dortige Build mit Java 8 und Maven 3.6.3 an den Anforderungen Java 27 und Maven 3.9 scheitert. Seine Jackson-2-, Woodstox- und StAX-Abhängigkeiten werden bereits auf die aktuellen Versionen angehoben.

Die Spring-Boot-Dokumentation nennt für 4.1.1 Java 26 als obere unterstützte Version. Java 27 wird hier entsprechend der Projektvorgabe verwendet; Änderungen an diesem Abhängigkeitsstand müssen deshalb mit `mvn clean verify` unter JDK 27 und dem Container-Starttest geprüft werden.

Für diesen Stand war `mvn --strict-checksums clean verify` mit JDK 27 erfolgreich: 131 Tests bestanden, ein optionaler Live-Test wurde übersprungen. Zusätzlich wurden das ausführbare JAR, die WebJar-Ressourcen, eine neue H2-Dateidatenbank und das Öffnen sowie Migrieren einer mit H2 2.4.240 angelegten Datenbank geprüft. Die Containerprüfung für beide Architekturen läuft im GitHub-Workflow.

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
- Meldet BGG-Market-Angebote bei bekanntem Bestpreis nur, wenn ihr Preis unter dem aktuell verfügbaren Vergleichspreis liegt. Ohne Bestpreis wird auch bei fehlenden oder nach allen Wiederholungen fehlerhaften Vergleichsdaten gemeldet.
- Prüft das Schnäppchenforum von unknowns.de auf neue Themen und speichert beziehungsweise meldet dafür Titel, Link und das Forumslogo. BoardGameGeek und brettspiel-angebote.de werden für diese Einträge nicht aufgerufen.
- Speichert Angebote, Zeitpunkte, BGG-Werte, Vergleichspreise und den Benachrichtigungsstatus dauerhaft in H2.
- Bereinigt Suchbegriffe für BoardGameGeek und brettspiel-angebote.de: führende und folgende Leerzeichen, Klammerzusätze mit Wörtern sowie die Begriffe „Stapelspiel“, „Würfelspiel“ und „Jubiläumsausgabe“ werden entfernt.
- Für brettspiel-angebote.de werden alle Klammerzusätze einschließlich der Klammern entfernt, auch rein numerische und verschachtelte Zusätze. Aus „Die Glasstraße (German first edition)“ wird der Suchbegriff „Die Glasstraße“.
- Überspringt BoardGameGeek und den Preisvergleich vollständig, wenn der Angebotsname „Bundle“ enthält.
- Übergibt eine vorhandene BoardGameGeek-ID an den Preisservice und übernimmt Preise nur bei bestätigter gleicher ID.
- Speichert neue Angebote und Preisänderungen mit einer kompakten Vorschau sowie dem damaligen Suchstatus bei brettspiel-angebote.de und BoardGameGeek im Activity Log.
- Versucht die Recherche je Dienst höchstens dreimal mit drei Minuten Abstand, auch bei fehlenden Treffern oder fehlendem Bestpreis. Suchverlauf und nächste Fälligkeit werden dauerhaft gespeichert; nach Abschluss wird auch ohne Bestpreis gemeldet.
- Protokolliert erfolgreiche und fehlgeschlagene Telegram-Sendeversuche ohne Nachrichteninhalt, Bot-Token oder Chat-ID.
- Lässt nicht verfügbare Werte wie Verfügbarkeit, Vergleichspreis, Bestpreis oder BGG-Daten in Telegram-Nachrichten vollständig weg.
- Meldet neue oder preislich veränderte Angebote, wenn sie günstiger als ein aktuell verfügbares Vergleichsangebot sind oder eine der Zusatzquellen keinen Treffer liefert.
- Ist ein Bestpreis verfügbar, werden Angebote zusätzlich nur bei einem aktuellen Preis von höchstens 110 % des Bestpreises gemeldet. Bei 20 € Bestpreis sind somit bis einschließlich 22 € erlaubt; ein höherer oder fehlender Angebotspreis verhindert die Meldung auch bei fehlenden BGG-Daten. Ohne Bestpreis wird das Angebot unabhängig vom Vergleichspreis gemeldet, auch nach wiederholt fehlgeschlagener Recherche. unknowns.de wird unabhängig von Preisvergleich und Bestpreis gemeldet. Initialimport-Pause und Deduplizierung gelten weiterhin.
- Verhindert mit einem Fingerabdruck aus Quelle, URL und Preis doppelte Meldungen.
- Aktualisiert alle Quellen alle fünf Minuten.
- Sendet sonntags um 18:00 Uhr (Europe/Berlin) einen kompakten Wochenreport mit neuen Angeboten/Beiträgen, davon versendeten und wegen der Bestpreisgrenze zurückgehaltenen Angeboten sowie deren verlinkten Namen.
- Ruft beim Anwendungsstart jede aktivierte Quelle und die „Scythe“-Preissuche über den Preisservice testweise ab, ohne Ergebnisse zu speichern, und meldet Trefferzahlen, Fehler sowie die kurze Commit-ID per Telegram oder im Anwendungslog.
- Sortiert die Angebotsübersicht absteigend nach der letzten inhaltlichen Aktualisierung.
- Durchsucht über „Titel suchen“ alle gespeicherten Angebots- und Forumstitel nach Teilbegriffen, unabhängig von Groß- und Kleinschreibung. Die Suche lässt sich mit dem Quellenfilter kombinieren und bleibt beim Seitenwechsel erhalten. „Suche zurücksetzen“ entfernt den Suchbegriff und behält die gewählte Quelle bei. Leere Suchbegriffe zeigen wieder alle Angebote der gewählten Quelle.
- Überwacht, wann pro aktiver Quelle zuletzt ein neuer Datensatz gespeichert wurde. Nach vier Tagen ohne neue Daten von Spiele-Offensive, Milan-Spiele oder dem BGG Market beziehungsweise nach 30 Tagen bei unknowns.de wird genau eine Warnung gesendet. Ein späterer neuer Datensatz aktiviert die Warnung für die nächste Ruhephase erneut.
- Fragt Vergleichspreis und historischen Bestpreis über die JSON-API des separaten Preisservices ab; Standardadresse ist `http://localhost:8077`.
- Überlässt dem Preisservice den Website-Abruf, die Suche, Identitätsprüfung, HTML-Auswertung und dessen Cache-Fallback.
- Protokolliert API-Adresse, HTTP-Status und Versuch auf `DEBUG`; technische Fehler bleiben auf `WARN` sichtbar.
- Prüft täglich um 08:00 Uhr Europe/Berlin mit „Magical Athlete“, ob Daten von BoardGameGeek abgefragt werden können. Nach einem fehlgeschlagenen Test wird beim ersten wieder erfolgreichen Lauf einmalig eine Entwarnung gesendet; weitere erfolgreiche Läufe bleiben still, bis erneut ein Fehler auftritt.

## Voraussetzungen

- JDK 27
- Maven 3.9 oder neuer
- Optional: Telegram-Bot und Chat-ID
- Optional, aber für BGG-Daten erforderlich: persönlicher BGG-API-Token
- Für den Preisvergleich: erreichbarer [brettspielpreise-Service](https://github.com/jensGiehl/brettspielpreise), standardmäßig unter `http://localhost:8077`. Der Service benötigt den Website-Zugriff und übernimmt die Browser- und Netzwerkkonfiguration.

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
| `GIT_COMMIT` | Commit-ID für die Footer-Version und die Statusmeldung des Start-Systemchecks | `unknown` |
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
| `PRICE_COMPARISON_URL` | Basis-URL des separaten Preisservices (`offers.sources.price-comparison`) | `http://localhost:8077` |
| `PRICE_COMPARISON_TIMEOUT` | Antwort-Timeout der Preisservice-API (`offers.http.price-comparison-timeout`) | `60s` |
| `LOOKUP_RETRY_DELAY` | Abstand zwischen den gespeicherten Rechercheversuchen | `3m` |
| `BG_PRICES_SOURCE_DIR` | Preisservice-Checkout für Docker Compose, einschließlich Dockerfile und Seccomp-Profil | `../brettspielpreise` |
| `BG_PRICES_IMAGE` | Name des von Compose lokal gebauten Preisservice-Images | `bg-prices:local` |
| `IPV6_PROXY_ENABLED` | Optionaler IPv6-Proxy im Preisservice-Container | `false` |
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

Die Quellen lassen sich außerdem direkt mit den Spring-Properties `offers.sources.spiele-offensive-enabled`, `offers.sources.milan-enabled`, `offers.sources.bgg-market-enabled` und `offers.sources.unknowns-enabled` einzeln ein- oder ausschalten. Alle vier sind standardmäßig aktiviert. Weitere Einstellungen wie Quell-URLs, Zeitpläne, HTTP-Timeout, Wiederholungsversuche und Parallelität der Milan-Detailabrufe befinden sich in `src/main/resources/application.yml` und können über die üblichen Spring-Boot-Konfigurationsmechanismen überschrieben werden. Temporäre HTTP-Fehler werden standardmäßig bis zu dreimal mit 750 Millisekunden Pause versucht. Die übergeordnete Recherche bei BoardGameGeek und brettspiel-angebote.de umfasst unabhängig von den HTTP-Versuchen höchstens drei Versuche je Dienst. Technische Fehler, fehlende Treffer und ein fehlender Bestpreis werden mit jeweils drei Minuten Abstand erneut geprüft. Diese Pause lässt sich über `LOOKUP_RETRY_DELAY` anpassen, beispielsweise mit `LOOKUP_RETRY_DELAY=5m`. Ein separater Scheduler prüft alle 30 Sekunden auf fällige Recherchen, ohne den Import während der Wartezeit zu blockieren. Erfolgreiche Recherchen werden nicht wiederholt; beim Preisvergleich gilt ein Treffer ohne Bestpreis weiterhin als unvollständig. Nicht konfigurierte und ausdrücklich übersprungene Recherchen enden sofort. Nach Abschluss aller erforderlichen Versuche wird auch ohne Bestpreis gemeldet; bei bekanntem Bestpreis gelten die bestehenden Preisgrenzen. Fehlgeschlagene Zustellungen werden zeitversetzt erneut versucht. Suchversuche und Fälligkeiten überleben Neustarts. Eine Preisänderung beginnt einen neuen Suchverlauf. Bereits gespeicherte, noch ungemeldete Angebote mit unvollständiger Recherche werden beim Update ebenfalls eingeplant; frühere Suchversuche lassen sich nicht nachträglich rekonstruieren. Höchstens zwei Milan-Detailseiten werden gleichzeitig geladen. Die HTTP-Retries gelten auch für den Preisservice: Netzwerkfehler, HTTP 429 und HTTP 5xx werden erneut versucht; HTTP 4xx außer 429 und Weiterleitungen enden sofort als Fehler. Der separate API-Antwort-Timeout beträgt 60 Sekunden, damit die laut API möglichen 45 Sekunden einschließlich Queue abgedeckt sind. Die übrigen HTTP-Abrufe behalten ihren bisherigen Timeout.

Das Schnäppchenforum von unknowns.de ist derzeit nur für angemeldete Benutzer erreichbar. Vor jedem Abruf meldet sich die Anwendung mit `UNKNOWNS_USERNAME` und `UNKNOWNS_PASSWORD` über das Login-Formular an. Die dabei gesetzten Session-Cookies werden ausschließlich im Arbeitsspeicher verwaltet und automatisch beim anschließenden Forenabruf mitgesendet. Die Zugangsdaten gehören nicht in die Versionsverwaltung oder in Logs. Wird die Quelle nicht benötigt, kann sie mit `UNKNOWNS_ENABLED=false` deaktiviert werden.

Mit `INITIAL_IMPORT=true` werden Angebote während der ersten zwei Stunden nach dem Anwendungsstart weiterhin vollständig importiert, angereichert und im Activity Log erfasst, aber nicht gemeldet. Danach werden Benachrichtigungen automatisch wieder aktiviert, auch wenn die Variable weiterhin auf `true` steht. Während des Zeitfensters zurückgestellte Angebote werden danach vom Scheduler erneut für den Versand geprüft, auch ohne Preisänderung; ausstehende Recherchen werden zuvor abgeschlossen.

## Web-Oberfläche

Die Angebotsübersicht ist unter `http://localhost:8080/` erreichbar. Das paginierte Activity Log befindet sich unter `http://localhost:8080/aktivitaeten` und ist direkt über die Hauptnavigation verlinkt. Es zeigt für Angebotsereignisse Bild, Name, Preisentwicklung, Quelle, Recherche-Retries sowie die Ergebnisse von brettspiel-angebote.de und BoardGameGeek. Neben dem Vergleichspreis zeigt die Übersicht auch den historischen Bestpreis. Ein grünes Versand-Icon kennzeichnet erfolgreiche Meldungen, ein graues Icon nicht versendete Meldungen. Der Tooltip erläutert den Status; ohne Telegram zählt wie bisher die erfolgreiche Log-Ausgabe. In der Übersicht gilt der Status für den aktuellen Angebotspreis, im Activity Log für das jeweilige Ereignis. Ältere Aktivitäten ohne belegbaren Versandstatus zeigen ein Fragezeichen. Erfolgreiche Telegram-Aktivitäten zeigen den versendeten Inhalt mit Zeilenumbrüchen und Links; Bildnachrichten werden als solche mit Beschriftung beziehungsweise Projektname und URL erfasst. Sehr lange Inhalte werden auf 10.000 Zeichen gekürzt. Bei älteren Einträgen ohne gespeicherten Inhalt erscheint ein entsprechender Hinweis. Telegram-Fehler enthalten HTTP-Status und Fehlermeldung beziehungsweise den Grund eines Netzwerk- oder Bildfehlers; Bot-Token und Chat-ID werden entfernt. Jeder Anwendungsstart erscheint mit Commit-ID, vollständigem Statusbericht und Versandstatus.

Unter `http://localhost:8080/fehlende-treffer` stehen zwei getrennte Listen der bereinigten Suchbegriffe, für die BoardGameGeek beziehungsweise brettspiel-angebote.de keinen Treffer geliefert hat. Technische Fehler, nicht konfigurierte Quellen und übersprungene Bundle-Angebote werden dort nicht als fehlende Treffer gezählt.

Auf der Detailseite zeigen noch erfolglose Recherchen für BoardGameGeek und brettspiel-angebote.de den verwendeten Suchbegriff, Datum und Uhrzeit in Europe/Berlin sowie „Versuch x von 3“ für jeden protokollierten Versuch. Bei direkter BGG-Abfrage wird die BGG-ID angezeigt. Während der Wartezeit erscheint zusätzlich der nächste geplante Versuch. Beim Preisvergleich bleibt der Suchverlauf auch bei einem Treffer ohne Bestpreis sichtbar.

Übersprungene Recherchen werden in der Oberfläche nicht mit dem Status „Nicht erforderlich“ dargestellt; der jeweilige Bereich bleibt stattdessen ausgeblendet.

BGG stellt API-Zugriffe nur mit einem gültigen Token bereit. Das verwendete [bggClient-Projekt](https://github.com/jensGiehl/bggClient) beschreibt die Einbindung des Tokens.

## Telegram-Verhalten

Normale Meldungen enthalten kompakt Name, Angebotspreis, Verfügbarkeit, verfügbaren Vergleichspreis, historischen Bestpreis, BGG-Bewertung, „Want to buy“, „Want in trade“ sowie klickbare Links. Wenn ein Angebotsbild vorhanden ist, wird es heruntergeladen und als PNG mit einem gut lesbaren Quellen-Badge oben rechts hochgeladen: **SO** für Spiele-Offensive, **Milan** für Milan-Spiele und **BGG** für den BGG Market. Spieleschmiede-Banner erhalten ebenfalls den Badge **SO** und werden weiterhin ohne Beschriftung gesendet. Angebote ohne Bild werden als Textmeldung versendet.

unknowns.de-Beiträge verwenden das [Forumslogo](https://unknowns.de/images/style-10/pageLogo-5cc3ef36.svg) als Foto mit Titel und Beitragslink in der Beschriftung. Das Logo wird mit [Apache Batik](https://xmlgraphics.apache.org/batik/) von SVG nach PNG umgewandelt; das gilt auch für bereits gespeicherte Beiträge ohne Bild. Das Unknowns-Logo erhält den blauen Hintergrund der Website, damit die weiße Schrift sichtbar bleibt. Leere Inkscape-Fließtexte werden vor der SVG-Umwandlung entfernt. Transparente Bereiche der übrigen Angebotsbilder werden weiß hinterlegt. Große Bilder werden unter Beibehaltung des Seitenverhältnisses auf höchstens 1200 Pixel je Seite verkleinert; bei kleinen Bildern wird die Bildfläche für einen lesbaren Badge erweitert. Die Bildverarbeitung läuft im Arbeitsspeicher und benötigt keinen zusätzlichen Docker-Mount. Das Docker-Image enthält die dafür benötigten Schriftarten.

Eine Meldung gilt erst dann als versendet, wenn Telegram den Aufruf erfolgreich bestätigt hat. Fehlerhafte Sendeversuche, einschließlich fehlgeschlagener Bildabrufe oder Bildverarbeitung, werden deshalb beim nächsten relevanten Lauf erneut versucht. Ohne Telegram-Konfiguration gilt die Ausgabe im Log als erfolgreiche lokale Meldung; Bilder werden dabei nicht heruntergeladen oder verarbeitet.

Direkt beim Start führt die Anwendung einen rein lesenden Systemcheck aus. Dafür ruft sie jeden aktivierten Scraper einmal vollständig auf und prüft, ob mindestens ein Ergebnis geliefert wird. Zusätzlich führt sie die Suche nach „Scythe“ über den Preisservice aus und erwartet verfügbare Preisdaten. Die dabei gefundenen Angebote und Prüfergebnisse werden weder gespeichert noch angereichert oder als einzelne Angebote gemeldet. Nach Abschluss wird genau eine Statusmeldung mit einem Haken (✅) je erfolgreich geprüfter Quelle und für den erfolgreichen Preisvergleichstest, möglichen Fehlern und der siebenstelligen Commit-ID versendet. Ohne Telegram-Konfiguration erscheint dieselbe Meldung im Anwendungslog. Startbericht und Zustellung werden dauerhaft im Activity Log erfasst. Mit `STARTUP_SYSTEM_CHECK_ENABLED=false` entfallen die externen Prüfungen; der Start wird weiterhin mit Commit-ID und dem Hinweis „Systemcheck deaktiviert“ protokolliert und gemeldet.

Der tägliche externe Health-Check prüft nur BoardGameGeek. Der Preisservice wird ausschließlich beim Anwendungsstart mit „Scythe“ geprüft; eine tägliche Preisservice-Abfrage samt Warnung und Entwarnung entfällt. Sobald ein betroffener Test wieder erfolgreich ist, folgt genau eine Telegram-Entwarnung. Bleibt der Test erfolgreich, werden keine weiteren Entwarnungen gesendet. Dieser Zustand wird dauerhaft in der Datenbank gespeichert und überlebt Anwendungsneustarts. Schlägt die Zustellung der Entwarnung fehl, wird sie beim nächsten erfolgreichen Lauf erneut versucht. Die Ruhezeit-Überwachung berücksichtigt nur aktivierte Scraper und wertet einen erstmals gespeicherten Eintrag als neue Daten. Ihr Alarmzustand liegt dauerhaft in der Datenbank: Während derselben Ruhephase wird nur einmal gewarnt, nach einem neuen Datensatz kann eine spätere Ruhephase erneut eine Warnung auslösen.

## Versandentscheidungen und Fehler

Auf jeder Angebotsdetailseite steht im Bereich **Telegram-Versand** die gespeicherte Entscheidung mit Begründung und Zeitpunkt: laufende Recherche, Initialimport-Pause (einschließlich Ende in Berliner Zeit), Preisfilter, bereits gemeldeter Preis, bestätigte Telegram-Zustellung, reine Log-Ausgabe ohne Telegram-Konfiguration oder technischer Versandfehler. Geplante Recherche- und Versandversuche werden mit Zeitpunkt angezeigt. Beispiel: 40 € für Men-Nefer bei 54,95 € verfügbarem Vergleichspreis und 49,85 € historischem Bestpreis erfüllen die Preisregeln; eine Initialimport-Pause kann den Versand trotzdem bis zwei Stunden nach dem Anwendungsstart zurückstellen. Mit `INITIAL_IMPORT=true` beginnt diese Pause bei jedem Neustart erneut.

Zurückstellungen und Ablehnungen werden mit Begründung in den Aktivitäten gespeichert. Bild-, Netzwerk- und Telegram-API-Fehler enthalten technische Details und einen Link zum betroffenen Angebot; sie sind zusätzlich auf dessen Detailseite sichtbar. Nach einem erfolgreichen Wiederholungsversuch bleiben frühere Fehler im Versandverlauf erhalten (die letzten zehn Ereignisse auf der Detailseite, der vollständige Verlauf in den Aktivitäten). Bot-Token und Chat-ID werden aus Fehlermeldungen entfernt. Fehlgeschlagene Meldungen erhalten keinen erfolgreichen Versandfingerabdruck und werden automatisch erneut geprüft. Vor diesem Update gespeicherte Einträge werden als unbekannte Versandentscheidung gekennzeichnet; die aktuellen Preisregeln und vorhandene Versandereignisse helfen bei der Einordnung, ohne einen historischen Grund zu behaupten.

## Wochenreport

Jeden Sonntag um 18:00 Uhr **Europe/Berlin** sendet die Anwendung den Report über den konfigurierten Telegram-Bot. Ohne Telegram-Konfiguration erscheint er im Anwendungslog. Der Zeitraum reicht vom vorherigen Sonntag um 18:00 Uhr einschließlich bis zum aktuellen Sonntag um 18:00 Uhr ausschließlich; Sommer- und Winterzeit werden berücksichtigt. Die Anwendung muss zum Versandzeitpunkt laufen.

Gezählt werden unterschiedliche, in diesem Zeitraum erstmals gespeicherte Angebote und Beiträge. Preisänderungen bereits bekannter Angebote, wiederholte Abrufe, Systemchecks, Warnungen und der Report selbst erhöhen die Zahlen nicht. „Versendet“ zählt nur erfolgreiche Angebotsmeldungen dieser neuen Einträge innerhalb des Zeitraums; ohne Telegram zählt die erfolgreiche Log-Ausgabe. Ein später in derselben Woche versendetes Angebot erscheint nur unter „Versendet“, auch wenn es zuvor zurückgehalten wurde.

„Zurückgehalten“ bezeichnet die bestehende Bestpreisgrenze: Angebotspreis über **110 % des historischen Bestpreises** oder fehlender Angebotspreis bei bekanntem Bestpreis. Ein fehlender Vergleichsbestpreis allein führt weiterhin nicht zur Zurückhaltung. Andere Gründe, etwa die Pause beim Initialimport, weitere Versandregeln oder Zustellfehler, werden bei Bedarf als „Sonstige nicht versendet“ ausgewiesen.

Die alphabetische Liste enthält pro zurückgehaltenem Angebot nur einen Aufzählungspunkt mit verlinktem Namen, ohne Preise, Bilder oder Linkvorschau. Lange Namen werden auf 120 Zeichen gekürzt. Umfangreiche Listen werden auf mehrere Telegram-Nachrichten verteilt, ohne Angebote wegzulassen. Auch bei null neuen Einträgen wird ein Report gesendet. Die Versand- und Zurückhaltungsereignisse werden ab dieser Version dauerhaft im Activity Log gespeichert; vor dem Update liegende Entscheidungen lassen sich daraus nicht nachträglich rekonstruieren.

Beispiel:

```text
Wochenreport
04.10.2026 18:00 – 11.10.2026 18:00 (Berlin)
Neu: 12 · Versendet: 9 · Zurückgehalten: 3

Bestpreisgrenze (+10 %) nicht erreicht:
• Angebot A
• Angebot B
• Angebot C
```

Die Namen sind in Telegram direkt mit dem jeweiligen Angebot verlinkt. Der Zeitplan steht in `offers.schedule.weekly-report-cron` (`0 0 18 * * SUN`). Schlägt ein Teil des Versands fehl, wird das im Anwendungslog gemeldet; der Report wird nicht automatisch nachgeholt.

## Docker

Das Image unterstützt `linux/amd64` und `linux/arm64`, einschließlich 64-Bit-Raspberry-Pi-Systemen. Es läuft mit Java 27 und als Benutzer `app` mit UID/GID `10001`. Der Build verwendet `maven:3.10.0-eclipse-temurin-27`, die Laufzeit `eclipse-temurin:27-jre`. Der GitHub-Workflow baut beide Architekturen einschließlich der Tests und veröffentlicht sie unter `ghcr.io/jensgiehl/bg-offers:latest`. Anschließend prüft er für beide Architekturen den Containerstart mit Host-Netzwerk auf Port 8089, die Web-Oberfläche und das Anlegen der Datenbank in einem eingebundenen Ordner; externe Abrufe sind für diese Prüfung deaktiviert.

### Docker und Compose auf dem Raspberry Pi installieren

Die Anleitung gilt für **Raspberry Pi OS mit 64 Bit** auf Basis von Debian Bookworm oder Trixie. `dpkg --print-architecture` muss `arm64` anzeigen. Docker verweist für [64-Bit-Raspberry-Pi-OS](https://docs.docker.com/engine/install/raspberry-pi-os/) auf seine [Debian-Installationsanleitung](https://docs.docker.com/engine/install/debian/).

Wenn Docker Engine aus dem offiziellen Docker-Paketrepository bereits installiert ist, Compose und die Hilfswerkzeuge ergänzen:

```bash
sudo apt-get update
sudo apt-get install -y docker-compose-plugin git curl
docker compose version
```

Für eine neue Installation zunächst das Docker-Paketrepository einrichten und danach Engine, Buildx und das [Compose-Plugin](https://docs.docker.com/compose/install/linux/) installieren:

```bash
dpkg --print-architecture
. /etc/os-release
printf '%s\n' "$VERSION_CODENAME"

sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -d -m 0755 /etc/apt/keyrings
sudo curl --fail --silent --show-error --location \
  https://download.docker.com/linux/debian/gpg \
  --output /etc/apt/keyrings/docker.asc
sudo chmod 0644 /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: $VERSION_CODENAME
Components: stable
Architectures: arm64
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker compose version
```

Bei einer vorhandenen Installation aus anderen Paketquellen zuerst die Hinweise zu kollidierenden Paketen in der Docker-Installationsanleitung beachten. Die folgenden Beispiele verwenden `docker` ohne `sudo`. Falls dafür noch die Berechtigung fehlt:

```bash
sudo usermod -aG docker "$USER"
```

Danach die SSH-Sitzung beenden und neu anmelden. Alternativ sämtliche Docker-Befehle mit `sudo` ausführen. Die Docker-Gruppe gewährt administrative Rechte auf dem Host.

### Beide Services mit Docker Compose starten

`compose.yaml` startet **BG Offers** und den Preisservice aus [jensGiehl/brettspielpreise](https://github.com/jensGiehl/brettspielpreise). Das Preisservice-Projekt hat laut seiner Dokumentation noch kein veröffentlichtes Registry-Image; Compose baut daher `bg-prices:local` aus einem benachbarten Checkout. BG Offers wird aus `ghcr.io/jensgiehl/bg-offers:latest` geladen. Für diese Einrichtung ist kein lokal installiertes Java oder Maven erforderlich.

Auf dem Pi beide Repositories nebeneinander ablegen:

```bash
mkdir -p "$HOME/brettspiele"
cd "$HOME/brettspiele"
git clone https://github.com/jensGiehl/bg-offers.git
git clone https://github.com/jensGiehl/brettspielpreise.git
cd bg-offers

if [ ! -f .env ]; then cp .env.example .env; fi
chmod 600 .env
mkdir -p data price-data
sudo chown -R 10001:10001 data price-data
sudo chmod -R u+rwX data price-data
```

Bei vorhandenen Checkouts direkt in das BG-Offers-Verzeichnis wechseln. In `.env` bei Bedarf `BGG_API_TOKEN`, `TELEGRAM_BOT_TOKEN` und `TELEGRAM_CHAT_ID` eintragen. unknowns.de ist in der Vorlage deaktiviert; zum Aktivieren `UNKNOWNS_ENABLED=true` und die Zugangsdaten setzen. Bereits vorhandene `.env`-Dateien bleiben erhalten. Liegt brettspielpreise anderswo, `BG_PRICES_SOURCE_DIR` auf dessen Verzeichnis setzen; standardmäßig ist es `../brettspielpreise`. `PRICE_COMPARISON_URL` muss für diese Konfiguration `http://localhost:8077` lauten.

Beide Container laufen mit dem **Host-Netzwerk** von Docker Engine auf Linux. So nutzt auch der Browser des Preisservices die Netzwerkkonfiguration des Pi. Das Seccomp-Profil für die Chromium-Sandbox wird aus `brettspielpreise/docker/seccomp_profile.json` übernommen. Unprivilegierte User-Namespaces müssen auf dem Host verfügbar sein. Weitere Browser- und IPv6-Einstellungen beschreibt die [Preisservice-README](https://github.com/jensGiehl/brettspielpreise#ipv6-quelladresse-und-browserdiagnose); dessen optionaler IPv6-Proxy lässt sich in `.env` mit `IPV6_PROXY_ENABLED=true` aktivieren.

| Service | Port am Host | Datenverzeichnis auf dem Host | Pfad im Container |
|---|---|---|---|
| BG Offers (`bg-offers`) | `8089` | `./data` | `/app/data/bg-offers` |
| Preisservice (`bg-prices`) | `8077` | `./price-data` | `/app/data/bg-prices` |

**8089 ist der Port am Host** für die Angebotsoberfläche. Im Host-Netzwerk lauscht die Anwendung selbst auf 8089; es gibt keine Portweiterleitung. Für die Preisservice-API gilt entsprechend 8077. Beide Ports müssen frei sein. Die H2-Datenbanken liegen in getrennten Bind-Mounts und bleiben bei Containerwechseln erhalten. `.env`, `data` und `price-data` sind von Git und dem BG-Offers-Image-Build ausgeschlossen. Frühere Java- oder Docker-Instanzen vor dem Wechsel stoppen, damit Port und Datenbank nur von einem Prozess genutzt werden.

Beim Umstieg von manuell gestarteten Containern deren Namen vor dem ersten Compose-Start freigeben (die eingebundenen Datenverzeichnisse bleiben erhalten):

```bash
docker stop --time 65 bg-offers bg-prices 2>/dev/null || true
docker rm bg-offers bg-prices 2>/dev/null || true
```

Im BG-Offers-Verzeichnis bauen und starten:

```bash
docker compose config --quiet
docker compose pull bg-offers
docker compose build --pull bg-prices
docker compose up -d --no-build
docker compose ps
docker compose logs --tail=100 -f bg-prices bg-offers
```

Der erste Preisservice-Build lädt JDK, Maven-Abhängigkeiten und das Playwright-Image und führt die Tests des Preisservice-Projekts aus; auf dem Pi kann das dauern. Der Preisservice erhält 1,5 GiB RAM-Limit, zwei CPUs und 256 MiB Shared Memory. Für beide Services und den Build entsprechend freien Arbeitsspeicher vorsehen.

Compose startet BG Offers erst, wenn `/actuator/health/readiness` des Preisservices erfolgreich ist. Die Bereitschaftsprüfung prüft die lokale API; sie ersetzt keine erfolgreiche Preisrecherche. Beim Start von BG Offers bleibt der „Scythe“-Systemcheck über die API aktiv. Der tägliche Scythe-Test ist in beiden Anwendungen deaktiviert; die täglichen BoardGameGeek- und Scraper-Prüfungen von BG Offers bleiben erhalten.

Die Oberfläche ist unter `http://<PI-IP>:8089` erreichbar. Die Preisservice-API lässt sich auf dem Pi prüfen:

```bash
curl --fail http://localhost:8077/actuator/health/readiness
curl -G http://localhost:8077/api/v1/prices \
  --data-urlencode 'name=Scythe' --data-urlencode 'bggId=169786'
```

Updates aus dem BG-Offers-Verzeichnis:

```bash
git pull --ff-only
git -C ../brettspielpreise pull --ff-only
docker compose pull bg-offers
docker compose build --pull bg-prices
docker compose up -d --no-build
```

Bei abweichendem `BG_PRICES_SOURCE_DIR` den Pfad im zweiten Befehl entsprechend anpassen. Den Preisservice stets neu bauen, wenn dessen Checkout aktualisiert wurde. `docker compose pull` allein aktualisiert das lokal gebaute Preisservice-Image nicht. `docker compose down` beendet beide Container; die Datenverzeichnisse bleiben erhalten. Vor einem Backup mit `docker compose stop` beide Services anhalten und `data` sowie `price-data` sichern. Nach einem Host-Neustart starten beide Container durch `restart: unless-stopped` wieder, sofern Docker aktiviert ist. Die `depends_on`-Bereitschaftsprüfung gilt beim Compose-Start, nicht für die automatische Startreihenfolge des Docker-Daemons.

### Nur BG Offers manuell starten

Wenn der Preisservice bereits unter `http://localhost:8077` läuft, kann BG Offers alternativ ohne Compose gestartet werden. Dieser Befehl ersetzt den BG-Offers-Container und verwendet die vorbereitete `.env` und Datenbank:

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

Auch hier ist **8089 der Port am Host**. Für Docker Desktop oder ein Bridge-Netzwerk folgt weiter unten ein Beispiel mit `-p 8089:8080`.

### Zugriffsfehler auf `/app/data` beheben

Bei `java.nio.file.AccessDeniedException: /app/data` kann H2 seine Datenbankdateien nicht anlegen oder öffnen. Flyway meldet den Fehler beim Verbindungsaufbau. Das Image läuft als Benutzer `app` mit UID/GID `10001`; das eingebundene Host-Verzeichnis und vorhandene Datenbankdateien müssen für diesen Benutzer lesbar und schreibbar sein. Verzeichnisse benötigen außerdem das Ausführungsrecht. Das `chown` im Dockerfile setzt nur die Rechte im Image. Ein [Bind-Mount](https://docs.docker.com/engine/storage/bind-mounts/#bind-mounting-over-existing-data) überdeckt dieses Verzeichnis mit dem Host-Verzeichnis und dessen Rechten.

Für die mitgelieferte Compose-Konfiguration auf dem Linux-Host im Projektverzeichnis ausführen:

```bash
docker compose stop bg-offers
mkdir -p data
sudo chown -R 10001:10001 data
sudo chmod -R u+rwX data
docker compose run --rm --no-deps --entrypoint sh bg-offers -c 'set -eu; probe=$(mktemp /app/data/.write-check.XXXXXX); rm "$probe"; if [ -e /app/data/bg-offers.mv.db ]; then test -r /app/data/bg-offers.mv.db; test -w /app/data/bg-offers.mv.db; fi'
docker compose up -d bg-offers
docker compose logs --tail=100 bg-offers
```

Der Schreibtest läuft als derselbe Benutzer wie die Anwendung und muss mit Exit-Code `0` enden, bevor die Anwendung wieder gestartet wird. Die vorhandene Datenbank bleibt erhalten; geändert werden Eigentümer und Zugriffsrechte im Datenverzeichnis.

Bei einem Start mit `docker run` zuerst mit `docker stop bg-offers` stoppen, die gleichen Rechte auf dem tatsächlich eingebundenen Host-Verzeichnis setzen und anschließend mit `docker start bg-offers` starten. Den verwendeten Mount und seinen Schreibmodus zeigt:

```bash
docker inspect bg-offers --format '{{range .Mounts}}{{println .Source "->" .Destination "writable=" .RW}}{{end}}'
```

Für `/app/data` muss `writable=true` erscheinen. Bei einem anderen Mount-Pfad die obigen Rechtebefehle auf dessen `Source` anwenden. Ein schreibgeschützter Mount muss in der Startkonfiguration korrigiert und der Container neu erstellt werden. `DB_PATH` muss weiterhin einen Datenbank-Dateipfad ohne Dateiendung enthalten, beispielsweise `/app/data/bg-offers`, und darf nicht nur `/app/data` sein.

### Image lokal bauen

```bash
docker build \
  --build-arg GIT_COMMIT="$(git rev-parse HEAD)" \
  -t bg-offers:local .
```

Für den lokalen Start im obigen `docker run` die letzte Zeile durch `bg-offers:local` ersetzen und `--pull=always` entfernen. Das lokale Image wird ebenfalls einschließlich Tests gebaut.

Maven fragt zuerst Maven Central und anschließend JitPack ab, das für `bggClient` benötigt wird. Dadurch werden Standardabhängigkeiten wie `xml-apis-ext` aus Maven Central geladen; JitPack hatte hierfür leere Dateien mit HTTP 200 geliefert. Beide Maven-Schritte im Docker-Build verwenden `--strict-checksums`, damit Downloads mit fehlenden oder falschen Prüfsummen abgelehnt werden. Änderungen an `pom.xml` erneuern auch die Docker-Schicht mit den vorgeladenen Abhängigkeiten.

### Betrieb mit einem eigenen Docker-Netzwerk

Für einen Preisservice auf dem Docker-Host und BG Offers im Bridge-Netzwerk kann folgender Start verwendet werden. Der Service muss auf einer für den Container erreichbaren Host-Adresse lauschen:

```bash
docker rm -f bg-offers 2>/dev/null

docker run -d \
  --name bg-offers \
  --pull=always \
  --init \
  --restart unless-stopped \
  -p 8089:8080 \
  --add-host host.docker.internal:host-gateway \
  --env-file .env \
  -e PRICE_COMPARISON_URL=http://host.docker.internal:8077 \
  -e SERVER_PORT=8080 \
  -e DB_PATH=/app/data/bg-offers \
  --mount "type=bind,source=$(pwd)/data,target=/app/data" \
  ghcr.io/jensgiehl/bg-offers:latest
```

Hier ist **8089 der Port am Host**, während die Anwendung im Container auf 8080 läuft. `./data` wird unter `/app/data` eingebunden und erhält die H2-Datenbank über Containerwechsel hinweg. `localhost` bezeichnet im Bridge-Netzwerk den BG-Offers-Container selbst; deshalb wird die Preisservice-URL im Beispiel auf `host.docker.internal` gesetzt. Laufen beide Dienste in einem gemeinsamen Docker-Netzwerk, stattdessen beispielsweise `--network brettspiele` und `PRICE_COMPARISON_URL=http://brettspielpreise:8077` verwenden (Dienstname und internen Service-Port anpassen). Details beschreibt die [Docker-Dokumentation zu Bridge-Netzwerken](https://docs.docker.com/engine/network/drivers/bridge/).

## Tests

```bash
mvn test
```

Der echte Live-Test sucht „Scythe“ über den separat laufenden Preisservice und erwartet einen positiven verfügbaren Preis. Die Basis-URL wird auch hier über `PRICE_COMPARISON_URL` gesetzt (Standard `http://localhost:8077`). Er ist standardmäßig deaktiviert, damit ein externer Ausfall den normalen Build nicht fehlschlagen lässt:

```bash
RUN_LIVE_PRICE_COMPARISON_TEST=true mvn -Dtest=PriceComparisonLiveTest test
```

Unter PowerShell:

```powershell
$env:RUN_LIVE_PRICE_COMPARISON_TEST="true"
mvn -Dtest=PriceComparisonLiveTest test
Remove-Item Env:RUN_LIVE_PRICE_COMPARISON_TEST
```

Die Tests prüfen unter anderem alle vier Quellen, den rein lesenden Start-Systemcheck mit Erfolgs-, Leer- und Fehlerfällen und dauerhaftem Startbericht, die BGG-Market-Feldzuordnung und Deduplizierung über `productid`, den direkten Einsatz von `objectid`, die Market-Preisprüfung bei bekanntem Bestpreis und den Versand ohne Bestpreis, den unknowns.de-Parser, Quellen-Badges, SVG-Logo-Konvertierung einschließlich leerer Inkscape-Fließtexte, PNG-Foto-Uploads mit UTF-8-Beschriftung und Bildfehlern, Gruppendeal-Mengen, Spieleschmiede-Filterung, Milan-Bildauswahl, HTTP- und Recherche-Wiederholungen, Namensnormalisierung, Bundle-Ausschluss, die Benachrichtigungsunterdrückung beim Initialimport, Telegram-Nachrichten ohne leere Werte, die einmaligen und erneut aktivierbaren Scraper-Health-Warnungen, den täglichen BoardGameGeek-Health-Check mit einmaliger Entwarnung nach einer Erholung, die Preisservice-API mit UTF-8-Suchparametern und optionaler BoardGameGeek-ID, Live- und Cache-Antworten, Teilresultate, alle API-Statuswerte, HTTP- und Timeout-Retries, die Kontrolle der BoardGameGeek-ID, Vergleichspreise sowie die Darstellung des Activity Logs und der Übersicht fehlender Treffer.

## Preisservice-API

Der Vertrag ist in der [OpenAPI-Datei von brettspielpreise](https://github.com/jensGiehl/brettspielpreise/blob/master/src/main/resources/openapi.yaml) beschrieben. BG Offers ruft ausschließlich `GET /api/v1/prices?name=Suchbegriff` auf, mit zusätzlichem `bggId`, sofern bekannt. Suchbegriffe werden wie bisher bereinigt und als UTF-8-Queryparameter codiert. Bundle-Angebote ohne BGG-ID werden weiterhin übersprungen. Abruf und Parsen der Website finden vollständig im Preisservice statt.

Die JSON-Statuswerte `FOUND`, `NOT_FOUND`, `SKIPPED` und `ERROR` werden auf die vorhandenen Recherche-Statuswerte abgebildet. Bei `FOUND` werden `url`, `availablePrice` und `bestPrice` übernommen, auch bei Teilresultaten oder einem vom Service gelieferten Cache-Fallback. Der Service prüft dessen Gültigkeit; laut API ist ein Fallback bis einen Kalendermonat nach `fetchedAt` gültig. Cache-Preise sind historische Snapshots und bestätigen keine aktuelle Verfügbarkeit. Ein historischer Bestpreis ohne verfügbaren Preis bleibt nutzbar; ein Treffer ohne Bestpreis bleibt wie bisher für weitere Rechercheversuche offen. Nicht-EUR-Preise, negative Preise und eine fehlende oder abweichende `matchedBggId` bei angeforderter ID werden als technische Fehler behandelt.

Der Startup-Systemcheck sucht weiterhin nach „Scythe“ über dieselbe API. Er prüft, ob der Service verfügbare Preisdaten liefert; ein gültiger Cache-Fallback kann diese Prüfung ebenfalls erfüllen. Der tägliche Preisvergleichstest ist entfernt. Der tatsächliche Live-Quellstatus des Preisservices ist separat über dessen `GET /api/v1/source-status` abrufbar.

Die bestehenden HTTP- und gespeicherten Recherche-Retries bleiben in BG Offers erhalten. Die vom Service gemeldeten Abkühlzeiten (`retryAt` beziehungsweise `Retry-After`) ersetzen den konfigurierten BG-Offers-Retry-Zeitplan nicht. Auch fehlerhafte oder nicht erreichbare Services enden nach den bestehenden Versuchsgrenzen; danach gelten unverändert die Benachrichtigungsregeln.

Alle Bestandteile dieser Anbindung liegen im Package `de.agiehl.bgoffers.pricecomparison`. Mit `PRICE_COMPARISON_LOG_LEVEL=DEBUG` oder `logging.level.de.agiehl.bgoffers.pricecomparison` lassen sich API-Aufrufe und HTTP-Status detaillierter protokollieren.
