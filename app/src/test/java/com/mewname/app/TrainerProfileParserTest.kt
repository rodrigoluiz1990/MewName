package com.mewname.app

import com.mewname.app.domain.TrainerProfileParser
import org.junit.Assert.*
import org.junit.Test

class TrainerProfileParserTest {
    @Test fun readsExplicitPortugueseFieldsAndSplitLabels() {
        val result = TrainerProfileParser.parse("""Nome do treinador:
RodBlack001
Nível: 40
Equipe: Instinto
1234 5678 9012""")
        assertEquals("RodBlack001", result.name)
        assertEquals("40", result.level)
        assertEquals("Instinto", result.team)
        assertEquals("123456789012", result.friendCode)
    }
    @Test fun readsIdentityOnlyWithProfileEvidence() {
        val result = TrainerProfileParser.parse("""EU
AMIGOS
SOCIAL
RodBlack001
40
DIARIO""")
        assertEquals("RodBlack001", result.name)
        assertEquals("40", result.level)
        assertNull(result.team)
    }
    @Test fun pokemonAndSearchScreensDoNotBecomeTrainerIdentity() {
        val result = TrainerProfileParser.parse("""PC 334
Wurmple
40
PS 92
12345 XP""")
        assertNull(result.name)
        assertNull(result.level)
        assertNull(result.friendCode)
    }
    @Test fun ignoresAmbiguousCodesAndAllowsPartialImport() {
        val ambiguous = TrainerProfileParser.parse("""1234 5678 9012
4321 8765 2109""")
        assertNull(ambiguous.friendCode)
        val partial = TrainerProfileParser.parse("""Trainer Code
1234 5678 9012""")
        assertEquals("123456789012", partial.friendCode)
        assertNull(partial.name)
    }
    @Test fun readsEnglishAndSpanishLabels() {
        assertEquals("Trainer42", TrainerProfileParser.parse("""Trainer name: Trainer42
Level: 35""").name)
        assertEquals("35", TrainerProfileParser.parse("""Nombre del entrenador: Trainer42
Nivel: 35""").level)
    }
    @Test fun suppliedProfileTranscriptionSeparatesBuddyFriendsAndNextLevel() {
        // Transcription of the supplied screenshot, not output from an OCR run.
        val text = """19:46
1 dispositivo
EU
AMIGOS
SOCIAL
288
RodWhite001
e ★Av⁹⁸Zacian
72
NÍVEL
138.658.735 / 9.500.000
2/4
Desbloqueie estas recompensas e muito mais no nível 73!
HISTÓRICO DE COMPANHEIRO
ÁLBUM
DIÁRIO
CUSTOMIZAR
TOTAL DE ATIVIDADES
Distância caminhada 6.215,5 km
Pokémon pegos 124.147
Poképaradas visitadas 39.591
Total de PE 241.261.735"""
        val result = TrainerProfileParser.parse(text)
        assertEquals("RodWhite001", result.name)
        assertEquals("72", result.level)
        assertNull(result.friendCode)
        assertNull(result.team)
    }
    @Test fun regionalLevelSurvivesOcrReadingOrderAndFriendCountOf72() {
        val result = TrainerProfileParser.parse(
            """EU AMIGOS SOCIAL
72
RodWhite001
Desbloqueie estas recompensas no nível 73""",
            listOf(
                com.mewname.app.domain.TrainerProfileLine("72", .48f, .08f),
                com.mewname.app.domain.TrainerProfileLine("RodWhite001", .06f, .13f),
                com.mewname.app.domain.TrainerProfileLine("65", .06f, .53f)
            ))
        assertEquals("RodWhite001", result.name)
        assertEquals("65", result.level)
    }    @Test fun actualExportedOcrWithAmgosAndInterleavedBlocksReadsIdentity() {
        val raw = """
19:46
RodWhite001
e tAV'"Zaciane
72
EU
NÍVEL
NEW
HISTÓRICO DE
COMPANHEIRO
AMGOS
138.658.735/ 9.500.000
ÁLBUM
28$
Desbloqueie estas recompensase muito mais no nível 7
1 dispositivo
Distância caminhada
Pokémon pegos
PE Total de PE
TOTAL DE ATIVIDADES
Poképaradas vis X
x1
DIÁRIO
SOCIAL
+
6.215,5 km
124.147
39.591
2/4
cUSTOMIZAR
x1
241.261.735
"""
        val result = TrainerProfileParser.parse(raw, listOf(
            com.mewname.app.domain.TrainerProfileLine("RodWhite001", .06885246f, .13237463f),
            com.mewname.app.domain.TrainerProfileLine("72", .06147541f, .51954275f)
        ))
        assertEquals("RodWhite001", result.name)
        assertEquals("72", result.level)
        assertNull(result.team)
        assertNull(result.friendCode)
    }}