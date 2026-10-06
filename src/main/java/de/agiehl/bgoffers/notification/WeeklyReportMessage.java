package de.agiehl.bgoffers.notification;

import de.agiehl.bgoffers.domain.WeeklyReport;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class WeeklyReportMessage {

    private static final int MAXIMUM_MESSAGE_LENGTH = 3900;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));

    private WeeklyReportMessage() {
    }

    static List<String> html(WeeklyReport report) {
        var heading = "<b>Wochenreport</b>\n"
                + DATE_FORMAT.format(report.from()) + " – " + DATE_FORMAT.format(report.until())
                + " (Berlin)\n"
                + "Neu: " + report.found() + " · Versendet: " + report.sent()
                + " · Zurückgehalten: " + report.withheld().size();
        var other = report.found() - report.sent() - report.withheld().size();
        if (other > 0) {
            heading += "\nSonstige nicht versendet: " + other;
        }
        if (report.withheld().isEmpty()) {
            return List.of(heading);
        }
        var messages = new ArrayList<String>();
        var message = new StringBuilder(heading)
                .append("\n\nBestpreisgrenze (+10 %) nicht erreicht:");
        var visibleLength = message.length();
        for (var offer : report.withheld()) {
            var name = compactName(offer.name());
            var line = "\n• <a href=\"" + escapeHtml(offer.url()) + "\">" + escapeHtml(name) + "</a>";
            if (visibleLength + name.length() + 3 > MAXIMUM_MESSAGE_LENGTH) {
                messages.add(message.toString());
                message = new StringBuilder("<b>Wochenreport · Fortsetzung</b>");
                visibleLength = message.length();
            }
            message.append(line);
            visibleLength += name.length() + 3;
        }
        messages.add(message.toString());
        return List.copyOf(messages);
    }

    static String plainText(WeeklyReport report) {
        return html(report).stream()
                .map(message -> org.jsoup.Jsoup.parse(message).wholeText())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private static String compactName(String name) {
        var compact = name.replaceAll("\\s+", " ").strip();
        var length = compact.codePointCount(0, compact.length());
        return length <= 120 ? compact : compact.substring(0, compact.offsetByCodePoints(0, 119)) + "…";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
