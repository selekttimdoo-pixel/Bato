package com.vipla.bato.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerbianProsodyTest {
    @Test fun regressionSetHasRequiredCoverage() {
        val entries=SerbianProsodyRegressionSet.words
        assertTrue(entries.size >= 53)
        assertTrue(entries.map { it.accent }.toSet().containsAll(SerbianAccent.entries))
        assertTrue(entries.any { it.postAccentualLengths.isNotEmpty() })
        assertTrue(entries.any { it.morphology.startsWith("V;") })
        assertTrue(entries.any { it.morphology.startsWith("N;") })
        listOf("razgovaram","kontinent","televizor").forEach { word ->
            assertTrue(entries.any { it.form==word }, "missing required word: $word")
        }
    }

    @Test fun morphologyResolvesSurfaceFormsAndAlternations() {
        val nominative=BatoSerbianGrammarGraph.resolve("grad")!!
        val genitive=BatoSerbianGrammarGraph.resolve("grada")!!
        assertEquals(nominative.lemma,genitive.lemma)
        assertFalse(nominative.accent==genitive.accent)
        assertEquals("N;M;GEN;SG",genitive.morphology)
    }

    @Test fun allRegressionWordsProduceExplicitPronunciation() {
        SerbianProsodyRegressionSet.words.forEach { entry ->
            val plan=SerbianProsodyResolver.resolve(entry.form)
            assertTrue(plan.resolved, "unresolved: ${entry.form}")
            assertTrue(plan.tokens.single().ipa.contains('ˈ'), "stress absent: ${entry.form}")
            assertTrue(plan.ssml.contains("<phoneme alphabet=\"ipa\""))
            assertTrue(plan.batoMarkup.contains("accent=\"${entry.accent.wire}\""))
        }
    }

    @Test fun postAccentualLengthReachesIpa() {
        val plan=SerbianProsodyResolver.resolve("razgovaram")
        assertTrue(plan.tokens.single().ipa.contains('ː'))
    }

    @Test fun unknownWordFailsClosedInsteadOfLettingTtsGuess() {
        val plan=SerbianProsodyResolver.resolve("nepostojećareč")
        assertFalse(plan.resolved)
        assertEquals(listOf("nepostojećareč"),plan.unresolved)
        assertEquals("",plan.ssml)
    }

    @Test fun sentenceFocusChangesProsodyMarkup() {
        val plain=SerbianProsodyResolver.resolve("Dragan razgovara sa Batom.")
        val focused=SerbianProsodyResolver.resolve("Dragan razgovara sa Batom.",setOf("Dragan"))
        assertTrue(plain.resolved)
        assertTrue(focused.resolved)
        assertTrue(focused.batoMarkup.contains("orth=\"Dragan\"") && focused.batoMarkup.contains("focus=\"true\""))
        assertTrue(focused.ssml.contains("volume=\"+2dB\""))
    }
}
