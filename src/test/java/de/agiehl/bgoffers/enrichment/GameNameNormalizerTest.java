package de.agiehl.bgoffers.enrichment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameNameNormalizerTest {

    private final GameNameNormalizer normalizer = new GameNameNormalizer();

    @Test
    void removesLanguageAndBundleAdditions() {
        assertThat(normalizer.searchTerm("DE: Scythe (deutsch) inkl. Promo"))
                .isEqualTo("Scythe");
        assertThat(normalizer.searchTerm("International - Biber Gang (english)"))
                .isEqualTo("Biber Gang");
    }

    @Test
    void removesRepeatedParenthesizedWordsAndSearchNoise() {
        assertThat(normalizer.searchTerm("  Zauberberg (Einzelszenario) Würfelspiel (deutsch/engl.) (Exp.)  "))
                .isEqualTo("Zauberberg");
        assertThat(normalizer.searchTerm("Stapelturm Stapelspiel (Erw.) (engl.) (international) (de) (dt.) (englisch)"))
                .isEqualTo("Stapelturm");
        assertThat(normalizer.searchTerm("Carcassonne Jubiläumsausgabe (deutsch)"))
                .isEqualTo("Carcassonne");
        assertThat(normalizer.searchTerm("Spiel (123)"))
                .isEqualTo("Spiel (123)");
    }

    @Test
    void removesAllParenthesizedContentForPriceComparison() {
        assertThat(normalizer.priceComparisonSearchTerm("  Die Glasstraße (German first edition)  "))
                .isEqualTo("Die Glasstraße");
        assertThat(normalizer.priceComparisonSearchTerm("Die (2026) Glasstraße () (German (first) edition)"))
                .isEqualTo("Die Glasstraße");
        assertThat(normalizer.priceComparisonSearchTerm("(German first edition) (123)"))
                .isEmpty();
        assertThat(normalizer.priceComparisonSearchTerm(null)).isEmpty();
    }

    @Test
    void recognizesBundleBeforeSearchNormalization() {
        assertThat(normalizer.isBundle("  Scythe Bundle (deutsch) ")).isTrue();
        assertThat(normalizer.isBundle("Bundle-Angebot")).isTrue();
        assertThat(normalizer.isBundle("Bundled Edition")).isFalse();
    }

    @Test
    void comparesNamesAccentAndCaseInsensitively() {
        assertThat(normalizer.similarity("Café", "CAFE")).isEqualTo(1.0);
        assertThat(normalizer.similarity("Scythe Bundle", "Scythe")).isEqualTo(1.0);
    }

    @Test
    void rejectsAConflictingEditionQualifier() {
        assertThat(normalizer.hasCompatibleEdition(
                "Kingdom Builder Anniversary Edition (international)",
                "Kingdom Builder: Empire Edition"))
                .isFalse();
        assertThat(normalizer.hasCompatibleEdition(
                "Kingdom Builder Anniversary Edition (international)",
                "Kingdom Builder: 10th Anniversary Edition"))
                .isTrue();
    }
}
