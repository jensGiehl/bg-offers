package de.agiehl.bgoffers.enrichment;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class GameNameNormalizer {

    private static final Pattern PARENTHESIZED_WORDS = Pattern.compile("\\([^)]*\\p{L}[^)]*\\)");
    private static final Pattern LANGUAGE_PREFIX = Pattern.compile(
            "(?i)^(?:de|deutsch|german|en|engl|englisch|english|international)\\s*[:|/-]\\s*");
    private static final Pattern BUNDLE_SUFFIX = Pattern.compile("(?i)\\s+(?:inkl\\..*|bundle|set)\\s*$");
    private static final Pattern SEARCH_NOISE = Pattern.compile("(?iu)\\b(?:Stapelspiel|Würfelspiel|Jubiläumsausgabe)\\b");
    private static final Pattern BUNDLE = Pattern.compile("(?iu)\\bBundle\\b");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDITION_QUALIFIER = Pattern.compile("\\b([a-z0-9]+)\\s+edition\\b");

    public String searchTerm(String value) {
        if (value == null) {
            return "";
        }
        var withoutPrefix = LANGUAGE_PREFIX.matcher(value.trim()).replaceFirst("");
        var withoutParentheses = PARENTHESIZED_WORDS.matcher(withoutPrefix).replaceAll(" ");
        var withoutNoise = SEARCH_NOISE.matcher(withoutParentheses).replaceAll(" ");
        return BUNDLE_SUFFIX.matcher(withoutNoise).replaceFirst("").replaceAll("\\s+", " ").trim();
    }

    public boolean isBundle(String value) {
        return value != null && BUNDLE.matcher(value.trim()).find();
    }

    public String normalized(String value) {
        var decomposed = Normalizer.normalize(searchTerm(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " und ");
        return NON_ALPHANUMERIC.matcher(decomposed).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    public double similarity(String expected, String candidate) {
        var left = normalized(expected);
        var right = normalized(candidate);
        if (left.equals(right)) {
            return 1.0;
        }
        if (!left.isBlank() && !right.isBlank() && (left.contains(right) || right.contains(left))) {
            return (double) Math.min(left.length(), right.length()) / Math.max(left.length(), right.length());
        }
        var leftTokens = tokens(left);
        var rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0;
        }
        var intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        var union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        return (double) intersection.size() / union.size();
    }

    public boolean hasCompatibleEdition(String expected, String candidate) {
        var expectedMatcher = EDITION_QUALIFIER.matcher(normalized(expected));
        if (!expectedMatcher.find()) {
            return true;
        }
        var candidateMatcher = EDITION_QUALIFIER.matcher(normalized(candidate));
        return candidateMatcher.find() && expectedMatcher.group(1).equals(candidateMatcher.group(1));
    }

    private Set<String> tokens(String value) {
        return Arrays.stream(value.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
