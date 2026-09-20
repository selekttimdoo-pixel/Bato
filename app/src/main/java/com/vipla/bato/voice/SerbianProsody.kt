package com.vipla.bato.voice

import java.text.Normalizer
import java.util.Locale

enum class SerbianAccent(val wire:String) {
    KRATKOSILAZNI("short_falling"), DUGOSILAZNI("long_falling"),
    KRATKOUZLAZNI("short_rising"), DUGOUZLAZNI("long_rising")
}
data class AccentEntry(
    val form:String, val lemma:String, val morphology:String, val accent:SerbianAccent,
    val stressedSyllable:Int, val postAccentualLengths:Set<Int>, val normativeNotation:String,
    val source:String="BATO_EDITORIAL_ACCENT_SET_V1",
    val reviewStatus:String="EDITORIAL_REVIEW_REQUIRED"
)
data class PronouncedToken(val surface:String,val entry:AccentEntry?,val ipa:String,val punctuation:Boolean,val focus:Boolean=false)
data class SerbianPronunciationPlan(
    val originalText:String,val normalizedText:String,val tokens:List<PronouncedToken>,
    val unresolved:List<String>,val batoMarkup:String,val ssml:String,
    val lexiconVersion:String,val grammarVersion:String
) { val resolved:Boolean get()=unresolved.isEmpty() }

/** Dedicated accent extension of BATO_ACTIVE_SERBIAN_LEXICON. */
object BatoSerbianAccentLexicon {
    const val VERSION="BATO_ACCENT_PROSODY_LEXICON_1"
    private val rows=listOf(
        "razgovaram|razgovarati|V;PRS;1SG|DU|3|4|razgovárām",
        "razgovaraš|razgovarati|V;PRS;2SG|DU|3|4|razgovárāš",
        "razgovaramo|razgovarati|V;PRS;1PL|DU|3|4|razgovárāmo",
        "kontinent|kontinent|N;M;NOM;SG|KU|2||kontìnent",
        "kontinenta|kontinent|N;M;GEN;SG|KU|2||kontìnenta",
        "televizor|televizor|N;M;NOM;SG|KU|2|3|telèvīzor",
        "televizora|televizor|N;M;GEN;SG|KU|2|3|telèvīzora",
        "grad|grad|N;M;NOM;SG|DS|1||grȃd",
        "grada|grad|N;M;GEN;SG|DU|1||gráda",
        "gradovi|grad|N;M;NOM;PL|DU|1||grádovi",
        "pas|pas|N;M;NOM;SG|KS|1||pȁs",
        "psa|pas|N;M;GEN;SG|DS|1||psȃ",
        "dan|dan|N;M;NOM;SG|DS|1||dȃn",
        "dana|dan|N;M;GEN;SG|DU|1||dána",
        "noć|noć|N;F;NOM;SG|DS|1||nȏć",
        "kuća|kuća|N;F;NOM;SG|KU|1||kùća",
        "kuće|kuća|N;F;GEN;SG|KU|1|2|kùćē",
        "voda|voda|N;F;NOM;SG|KU|1||vòda",
        "vodom|voda|N;F;INS;SG|KU|1|2|vòdōm",
        "ruka|ruka|N;F;NOM;SG|DU|1||rúka",
        "ruke|ruka|N;F;GEN;SG|DU|1|2|rúkē",
        "glava|glava|N;F;NOM;SG|DU|1||gláva",
        "glave|glava|N;F;GEN;SG|DU|1|2|glávē",
        "žena|žena|N;F;NOM;SG|DU|1||žéna",
        "žene|žena|N;F;GEN;SG|DU|1|2|žénē",
        "dete|dete|N;N;NOM;SG|DS|1||dȇte",
        "deteta|dete|N;N;GEN;SG|DU|1||déteta",
        "sunce|sunce|N;N;NOM;SG|KS|1||sȕnce",
        "more|more|N;N;NOM;SG|KS|1||mȍre",
        "zemlja|zemlja|N;F;NOM;SG|KS|1||zȅmlja",
        "Srbija|Srbija|PROPN;F;NOM;SG|KS|1||Sȑbija",
        "Beograd|Beograd|PROPN;M;NOM;SG|DS|1||Bȇograd",
        "Beograda|Beograd|PROPN;M;GEN;SG|DU|1||Béograda",
        "Dragan|Dragan|PROPN;M;NOM;SG|KS|1||Drȁgan",
        "Bato|Bato|PROPN;M;VOC;SG|DS|1||Bȃto",
        "radim|raditi|V;PRS;1SG|KS|1|2|rȁdīm",
        "radiš|raditi|V;PRS;2SG|KS|1|2|rȁdīš",
        "radio|raditi|V;PTCP;M;SG|KS|1||rȁdio",
        "pišem|pisati|V;PRS;1SG|DS|1|2|pȋšēm",
        "pisao|pisati|V;PTCP;M;SG|KS|1||pȉsao",
        "govorim|govoriti|V;PRS;1SG|KU|1|3|gòvorīm",
        "slušam|slušati|V;PRS;1SG|DU|1|2|slúšām",
        "čitam|čitati|V;PRS;1SG|DS|1|2|čȋtām",
        "razumem|razumeti|V;PRS;1SG|KU|2|3|razùmēm",
        "pamtim|pamtiti|V;PRS;1SG|KS|1|2|pȁmtīm",
        "istorija|istorija|N;F;NOM;SG|KU|2||istòrija",
        "imperija|imperija|N;F;NOM;SG|KU|2||impèrija",
        "asistent|asistent|N;M;NOM;SG|KU|2||asìstent",
        "parlament|parlament|N;M;NOM;SG|KU|1||pàrlament",
        "administrator|administrator|N;M;NOM;SG|KU|4||administràtor",
        "radijator|radijator|N;M;NOM;SG|KU|3||radijàtor",
        "telefon|telefon|N;M;NOM;SG|KU|1||tèlefon",
        "kompjuter|kompjuter|N;M;NOM;SG|KU|2||kompjùter",
        "mikrofon|mikrofon|N;M;NOM;SG|KU|1||mìkrofon",
        "Android|Android|PROPN;M;NOM;SG|KU|1||Àndroid",
        "Rim|Rim|PROPN;M;NOM;SG|DS|1||Rȋm",
        "Rimsko|rimski|ADJ;N;NOM;SG|KS|1||rȉmsko",
        "carstvo|carstvo|N;N;NOM;SG|DS|1||cȃrstvo",
        "Romeji|Romej|N;M;NOM;PL|KU|2||Romèji",
        "Konstantinopolj|Konstantinopolj|PROPN;M;NOM;SG|KU|4||Konstantinòpolj",
        "razgovara|razgovarati|V;PRS;3SG|DU|3|4|razgovárā",
        "sa|sa|PREP|KS|1||sȁ",
        "Batom|Bato|PROPN;M;INS;SG|DS|1|2|Bȃtom",
        "palo|pasti|V;PTCP;N;SG|KS|1||pȁlo",
        "zapadno|zapadni|ADJ;N;NOM;SG|KU|1||zàpadno",
        "jeste|biti|AUX;PRS;3SG|KS|1||jȅste",
        "danas|danas|ADV|DS|1||dȃnas",
        "sutra|sutra|ADV|KS|1||sȕtra",
        "ovde|ovde|ADV|KS|1||ȍvde",
        "samo|samo|ADV|KS|1||sȁmo",
        "nije|biti|AUX;PRS;3SG;NEG|DS|1||nȋje",
        "je|biti|AUX;PRS;3SG;CLITIC|KS|1||jȅ",
        "da|da|CONJ|KS|1||dȁ",
        "ali|ali|CONJ|KS|1||ȁli"
    )
    private fun accent(code:String)=when(code){
        "KS"->SerbianAccent.KRATKOSILAZNI; "DS"->SerbianAccent.DUGOSILAZNI
        "KU"->SerbianAccent.KRATKOUZLAZNI; else->SerbianAccent.DUGOUZLAZNI
    }
    val entries=rows.map { row ->
        val p=row.split('|')
        AccentEntry(p[0],p[1],p[2],accent(p[3]),p[4].toInt(),
            p[5].split(',').filter(String::isNotBlank).map(String::toInt).toSet(),p[6])
    }
    private val forms=entries.associateBy { it.form.lowercase(Locale.forLanguageTag("sr-RS")) }
    fun resolve(surface:String)=forms[surface.lowercase(Locale.forLanguageTag("sr-RS"))]
}

/** Surface-form and inflection bridge to BATO_SERBIAN_GRAMMAR_GRAPH. */
object BatoSerbianGrammarGraph {
    const val VERSION="BATO_SERBIAN_GRAMMAR_GRAPH_1"
    fun resolve(surface:String)=BatoSerbianAccentLexicon.resolve(surface)
}

object SerbianG2p {
    private val vowels=setOf('a','e','i','o','u')
    private val digraphs=mapOf("dž" to "dʒ","lj" to "ʎ","nj" to "ɲ")
    private val phonemes=mapOf('c' to "ts",'č' to "tʃ",'ć' to "tɕ",'đ' to "dʑ",'g' to "ɡ",'h' to "x",'š' to "ʃ",'v' to "ʋ",'ž' to "ʒ")
    fun toIpa(surface:String,entry:AccentEntry):String {
        val word=Normalizer.normalize(surface.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        val nuclei=word.indices.filter { word[it] in vowels || word[it]=='r' && (it==0 || word.getOrNull(it-1) !in vowels) && word.getOrNull(it+1) !in vowels }
        val stress=nuclei.getOrNull(entry.stressedSyllable-1)?:0
        val longNuclei=entry.postAccentualLengths.mapNotNull { nuclei.getOrNull(it-1) }.toSet()
        val out=StringBuilder(); var i=0
        while(i<word.length){
            if(i==stress) out.append('ˈ')
            val pair=word.substring(i,minOf(i+2,word.length))
            val di=digraphs[pair]
            if(di!=null){out.append(di);i+=2;continue}
            val c=word[i];out.append(phonemes[c]?:c)
            if(c in vowels && (i in longNuclei || i==stress && entry.accent in setOf(SerbianAccent.DUGOSILAZNI,SerbianAccent.DUGOUZLAZNI))) out.append('ː')
            i++
        }
        return out.toString()
    }
}

object SerbianProsodyResolver {
    private val tokenRegex=Regex("[\\p{L}]+|[0-9]+|[^\\p{L}0-9\\s]")
    private val punctuation=Regex("[^\\p{L}0-9]+")
    fun resolve(text:String,contrastiveFocus:Set<String> = emptySet()):SerbianPronunciationPlan {
        val normalized=SerbianSpeechNormalizer.normalize(text)
        val focus=contrastiveFocus.map { it.lowercase() }.toSet()
        val tokens=tokenRegex.findAll(normalized).map { it.value }.map { surface ->
            if(punctuation.matches(surface)) PronouncedToken(surface,null,"",true)
            else BatoSerbianGrammarGraph.resolve(surface).let { entry ->
                PronouncedToken(surface,entry,entry?.let { SerbianG2p.toIpa(surface,it) }.orEmpty(),false,surface.lowercase() in focus)
            }
        }.toList()
        val unresolved=tokens.filter { !it.punctuation && it.entry==null }.map { it.surface }.distinct()
        fun xml(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
        val markup=buildString {
            append("<bato-prosody version=\"1\">")
            tokens.forEach { t ->
                if(t.punctuation) append("<p>${xml(t.surface)}</p>") else t.entry?.let { e ->
                    append("<w orth=\"${xml(t.surface)}\" lemma=\"${xml(e.lemma)}\" morph=\"${xml(e.morphology)}\" accent=\"${e.accent.wire}\" syllable=\"${e.stressedSyllable}\" postlength=\"${e.postAccentualLengths.sorted().joinToString(",")}\" ipa=\"${xml(t.ipa)}\" focus=\"${t.focus}\"/>")
                } ?: append("<unresolved orth=\"${xml(t.surface)}\"/>")
            }
            append("</bato-prosody>")
        }
        val ssml=if(unresolved.isEmpty()) buildString {
            append("<speak version=\"1.0\" xml:lang=\"sr-RS\"><voice name=\"sr-RS-NicholasNeural\">")
            tokens.forEach { t ->
                if(t.punctuation) append(xml(t.surface)) else {
                    val contour=when(t.entry!!.accent){
                        SerbianAccent.KRATKOSILAZNI->"(0%,+8%)(100%,-10%)"; SerbianAccent.DUGOSILAZNI->"(0%,+10%)(100%,-12%)"
                        SerbianAccent.KRATKOUZLAZNI->"(0%,-6%)(100%,+8%)"; SerbianAccent.DUGOUZLAZNI->"(0%,-8%)(100%,+10%)"
                    }
                    val rate=if(t.entry.accent in setOf(SerbianAccent.DUGOSILAZNI,SerbianAccent.DUGOUZLAZNI)) "-7%" else "+0%"
                    val volume=if(t.focus) "+2dB" else "+0dB"
                    append("<prosody rate=\"$rate\" volume=\"$volume\" contour=\"$contour\"><phoneme alphabet=\"ipa\" ph=\"${xml(t.ipa)}\">${xml(t.surface)}</phoneme></prosody> ")
                }
            }
            append("</voice></speak>")
        } else ""
        return SerbianPronunciationPlan(text,normalized,tokens,unresolved,markup,ssml,
            BatoSerbianAccentLexicon.VERSION,BatoSerbianGrammarGraph.VERSION)
    }
}

object SerbianProsodyRegressionSet {
    val words=BatoSerbianAccentLexicon.entries
    val contrastiveSentences=listOf(
        "DRAGAN razgovara sa Batom." to setOf("Dragan"),
        "Dragan razgovara sa BATOM." to setOf("Batom"),
        "Rimsko carstvo nije palo, ali zapadno carstvo jeste." to setOf("Rimsko","zapadno")
    )
}
