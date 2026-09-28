package de.agiehl.bgoffers.scraper;

import de.agiehl.bgoffers.config.OfferProperties;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class CurlHttpTransport implements AutoCloseable {

    private static final String METADATA_FORMAT =
            "%{http_code}\t%{url_effective}\t%{content_type}\t%header{server}\t%{remote_ip}\t%{http_version}";

    private final OfferProperties properties;
    private Path cookieFile;

    CurlHttpTransport(OfferProperties properties) {
        this.properties = properties;
    }

    Response get(
            URI uri,
            String accept,
            URI referer,
            boolean ajaxRequest,
            Map<String, String> headers) throws IOException, InterruptedException {
        var responseFile = Files.createTempFile("bg-offers-price-comparison-response-", ".body");
        try {
            var command = command(uri, accept, referer, ajaxRequest, headers, responseFile);
            var process = new ProcessBuilder(command).start();
            var metadata = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            var maximumWait = properties.http().timeout().plusSeconds(5).toMillis();
            if (!process.waitFor(maximumWait, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("curl hat das konfigurierte Zeitlimit überschritten");
            }
            if (process.exitValue() != 0) {
                throw new IOException(errorOutput.isBlank()
                        ? "curl wurde mit Exit-Code %d beendet".formatted(process.exitValue())
                        : errorOutput);
            }
            return response(metadata, Files.readAllBytes(responseFile));
        } finally {
            Files.deleteIfExists(responseFile);
        }
    }

    Map<String, String> cookies() {
        if (cookieFile == null || !Files.exists(cookieFile)) {
            return Map.of();
        }
        try {
            var cookies = new LinkedHashMap<String, String>();
            for (var line : Files.readAllLines(cookieFile, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") && !line.startsWith("#HttpOnly_")) {
                    continue;
                }
                var fields = line.split("\\t", 7);
                if (fields.length == 7) {
                    cookies.put(fields[5], fields[6]);
                }
            }
            return Map.copyOf(cookies);
        } catch (IOException exception) {
            throw new SourceAccessException("Cookie-Datei des Preisvergleichs konnte nicht gelesen werden", exception);
        }
    }

    void reset() {
        close();
    }

    @Override
    public void close() {
        if (cookieFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(cookieFile);
        } catch (IOException ignored) {
        } finally {
            cookieFile = null;
        }
    }

    private List<String> command(
            URI uri,
            String accept,
            URI referer,
            boolean ajaxRequest,
            Map<String, String> headers,
            Path responseFile) throws IOException {
        var cookies = cookieFile();
        var timeoutSeconds = timeoutSeconds(properties.http().timeout());
        var command = new ArrayList<>(List.of(
                "curl",
                "--silent",
                "--show-error",
                "--connect-timeout", timeoutSeconds,
                "--max-time", timeoutSeconds,
                "--output", responseFile.toString(),
                "--write-out", METADATA_FORMAT,
                "--cookie", cookies.toString(),
                "--cookie-jar", cookies.toString(),
                "--user-agent", properties.http().userAgent()));
        addHeader(command, "Accept", accept);
        addHeader(command, "Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7");
        if (ajaxRequest) {
            addHeader(command, "X-Requested-With", "XMLHttpRequest");
            addHeader(command, "Sec-Fetch-Dest", "empty");
            addHeader(command, "Sec-Fetch-Mode", "cors");
            addHeader(command, "Sec-Fetch-Site", "same-origin");
            addHeader(command, "Priority", "u=1, i");
        } else {
            addHeader(command, "Sec-Fetch-Dest", "document");
            addHeader(command, "Sec-Fetch-Mode", "navigate");
            addHeader(command, "Sec-Fetch-Site", referer == null ? "none" : "same-origin");
            addHeader(command, "Sec-Fetch-User", "?1");
            addHeader(command, "Upgrade-Insecure-Requests", "1");
            addHeader(command, "Priority", "u=0, i");
        }
        if (referer != null) {
            addHeader(command, "Referer", referer.toString());
        }
        headers.forEach((name, value) -> addHeader(command, name, value));
        command.add(uri.toString());
        return List.copyOf(command);
    }

    private Path cookieFile() throws IOException {
        if (cookieFile == null) {
            cookieFile = Files.createTempFile("bg-offers-price-comparison-", ".cookies");
        }
        return cookieFile;
    }

    private void addHeader(List<String> command, String name, String value) {
        command.add("--header");
        command.add(name + ": " + value);
    }

    private String timeoutSeconds(Duration timeout) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0.001, timeout.toMillis() / 1_000.0));
    }

    private Response response(String metadata, byte[] body) throws IOException {
        var fields = metadata.split("\\t", -1);
        if (fields.length != 6) {
            throw new IOException("curl lieferte %d statt 6 Antwortmetadaten".formatted(fields.length));
        }
        try {
            return new Response(
                    Integer.parseInt(fields[0]),
                    URI.create(fields[1]),
                    fields[2].isBlank() ? null : fields[2],
                    fields[3].isBlank() ? null : fields[3],
                    fields[4].isBlank() ? null : fields[4],
                    fields[5].isBlank() ? null : fields[5],
                    body);
        } catch (IllegalArgumentException exception) {
            throw new IOException("curl lieferte ungültige Antwortmetadaten", exception);
        }
    }

    record Response(
            int statusCode,
            URI uri,
            String contentType,
            String server,
            String remoteAddress,
            String httpVersion,
            byte[] body) {

        String ipVersion() {
            if (remoteAddress == null) {
                return "unbekannt";
            }
            return remoteAddress.contains(":") ? "IPv6" : "IPv4";
        }

        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
