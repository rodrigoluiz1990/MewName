package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PersonalCollection
import com.mewname.app.domain.PersonalCollectionGroup as Group
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.VivillonPattern
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonalCollectionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun dexGridGroupsRegionalFormsWithoutChangingSavedTargets() {
        val regional = target("Arcanine (Hisui)")
        PersonalCollection.mark(context, regional, Group.SHINY, true)
        val catalog = com.mewname.app.domain.CollectionDexCatalog.load(context, listOf(regional))
        assertEquals(catalog.map { it.dex }.distinct().size, catalog.size)
        assertEquals(catalog.map { it.dex }.sorted(), catalog.map { it.dex })
        assertTrue(catalog.single { it.dex == 59 }.targets.any { it.key == regional.key })
        assertTrue(Group.SHINY in PersonalCollection.read(context).entries.getValue(regional.key).groups)
        assertTrue(catalog.single { it.dex == 3 }.targets.any { it.key.contains("VENUSAUR_COPY_2019") })
    }
    @Test fun gameCostumesHaveIndependentCaptureKeysAndDoNotInheritMega() {
        val catalog = com.mewname.app.domain.CollectionDexCatalog.load(context)
        val venusaur = catalog.single { it.dex == 3 }
        val costume = venusaur.targets.single { it.key.contains("GAME_FORM:VENUSAUR_COPY_2019") }
        PersonalCollection.mark(context, costume, Group.NORMAL, true)
        assertFalse(PersonalCollection.supports(costume, Group.MEGA))
        assertEquals(setOf(costume.key), PersonalCollection.read(context).entries.keys)
        assertTrue(com.mewname.app.domain.PersonalCollectionImages(context).url(costume)!!.contains("COPY_2019"))
    }
    @Test fun legacyPikachuCostumesUseRealGameSpritesAndStaySeparateFromBase() {
        val catalog = com.mewname.app.domain.CollectionDexCatalog.load(context)
        val pikachu = catalog.single { it.dex == 25 }
        val costumes = pikachu.targets.filter { it.key in pikachu.costumeKeys }
        assertTrue(costumes.count { it.key.contains("GAME_COSTUME:") } >= 34)
        val images = com.mewname.app.domain.PersonalCollectionImages(context)
        val costume = costumes.first { it.key.contains("GAME_COSTUME:") }
        val url = requireNotNull(images.url(costume))
        assertTrue(url.contains("PokeMiners/pogo_assets"))
        assertTrue(url.substringAfterLast('/').startsWith("pm25.c"))
        assertFalse(PersonalCollection.supports(costume, Group.GIGANTAMAX))
        assertFalse(target("Pikachu").key in pikachu.costumeKeys)
        val helmet = costumes.single { it.key.contains("GAME_FORM:PIKACHU_GLASS_HELMET_2026") }
        assertTrue(images.url(helmet)!!.endsWith("/pm25.fGLASS_HELMET_2026.icon.png"))
    }
    @Test fun legacyEntriesMigrateAndUnchangedEntriesAreReused() {
        val a = target("Pikachu")
        val b = target("Eevee")
        val prefs = PersonalCollection.preferences(context)
        val legacy = org.json.JSONArray().put(org.json.JSONObject()
            .put("key", a.key).put("label", a.label)
            .put("groups", org.json.JSONArray(listOf("SHINY"))))
        prefs.edit().putString("entries", legacy.toString()).commit()
        assertEquals(setOf(Group.SHINY), PersonalCollection.read(context).entries.getValue(a.key).groups)
        PersonalCollection.mark(context, b, Group.NORMAL, true)
        assertFalse(prefs.contains("entries"))
        assertEquals(setOf(a.key, b.key), prefs.getStringSet("entry_keys", null))
        val before = PersonalCollection.read(context).entries
        val savedA = prefs.getString("entry:" + a.key, null)
        PersonalCollection.mark(context, b, Group.SHINY, true)
        val after = PersonalCollection.read(context).entries
        assertEquals(savedA, prefs.getString("entry:" + a.key, null))
        assertSame(before[a.key], after[a.key])
        assertEquals(setOf(Group.NORMAL, Group.SHINY), after.getValue(b.key).groups)
        PersonalCollection.setEnabled(context, Group.NORMAL, false)
        assertSame(after, PersonalCollection.read(context).entries)
        prefs.edit().clear().commit()
        assertTrue(PersonalCollection.read(context).entries.isEmpty())
    }
    @Before fun reset() { PersonalCollection.preferences(context).edit().clear().commit() }
    private fun target(name: String) = PersonalCollection.target(context,PokemonScreenData(pokemonName = name))!!

    @Test fun customOrderSurvivesOtherCollectionChangesAndKeepsHiddenPosition() {
        val pokemon = target("Pikachu")
        val wanted = listOf(Group.IV100) + Group.entries.filter { it != Group.IV100 }
        PersonalCollection.setOrder(context, wanted)
        PersonalCollection.setEnabled(context, Group.IV100, false)
        PersonalCollection.mark(context, pokemon, Group.SHINY, true)
        PersonalCollection.setShowInSuggestedNames(context, true)
        assertEquals(wanted, PersonalCollection.read(context).order)
        PersonalCollection.setEnabled(context, Group.IV100, true)
        val snapshot = PersonalCollection.read(context)
        assertEquals(Group.IV100, snapshot.order.filter { it in snapshot.enabled }.first())
        assertEquals(setOf(Group.SHINY), snapshot.entries[pokemon.key]!!.groups)
        assertTrue(snapshot.showInSuggestedNames)
        val second = listOf(Group.XXS) + wanted.filter { it != Group.XXS }
        PersonalCollection.setOrder(context, second)
        assertEquals(second, PersonalCollection.read(context).order)
    }

    @Test fun savedOrderIgnoresObsoleteIdsDuplicatesAndAppendsMissingGroups() {
        PersonalCollection.preferences(context).edit()
            .putString("group_order", "[\"IV100\",\"REMOVED\",\"IV100\",\"XXS\"]").commit()
        val order = PersonalCollection.read(context).order
        assertEquals(listOf(Group.IV100, Group.XXS), order.take(2))
        assertEquals(Group.entries.size, order.size)
        assertEquals(Group.entries.toSet(), order.toSet())
        PersonalCollection.preferences(context).edit().putString("group_order", "invalid").commit()
        assertEquals(Group.entries.toList(), PersonalCollection.read(context).order)
    }

    @Test fun regionalFormsAreIndependentAndAliasesShareTheirEntry() {
        val normal = target("Arcanine")
        val hisui = target("Arcanine (Hisui)")
        assertNotEquals(normal.key,hisui.key)
        assertEquals(hisui.key,target("Hisuian Arcanine").key)
        PersonalCollection.mark(context,normal,Group.SHINY,true)
        assertNull(PersonalCollection.read(context).entries[hisui.key])
        assertEquals(setOf(Group.SHINY),PersonalCollection.read(context).entries[normal.key]!!.groups)
    }

    @Test fun groupsPersistIndependentlyAndDisablingDoesNotDeleteCaptures() {
        val pokemon = target("Pikachu")
        PersonalCollection.mark(context,pokemon,Group.IV100,true)
        PersonalCollection.mark(context,pokemon,Group.GREAT,true)
        PersonalCollection.setEnabled(context,Group.IV100,false)
        assertFalse(Group.IV100 in PersonalCollection.read(context).enabled)
        assertEquals(setOf(Group.IV100,Group.GREAT),PersonalCollection.read(context).entries[pokemon.key]!!.groups)
        PersonalCollection.mark(context,pokemon,Group.GREAT,false)
        assertEquals(setOf(Group.IV100),PersonalCollection.read(context).entries[pokemon.key]!!.groups)
        PersonalCollection.mark(context,pokemon,Group.IV100,false)
        assertTrue(PersonalCollection.read(context).entries.isEmpty())
    }

    @Test fun noCollectionIsMarkedFromOcrOrPvpResultsAutomatically() {
        PersonalCollection.target(context,PokemonScreenData(pokemonName="Pikachu",isShiny=true,ivPercent=100,pvpRank=1))
        assertTrue(PersonalCollection.read(context).entries.isEmpty())
        Group.entries.forEach { PersonalCollection.setEnabled(context,it,false) }
        assertTrue(PersonalCollection.read(context).enabled.isEmpty())
    }

    @Test fun uniqueFormsCannotBeAccidentallyMergedOrMarkedWithoutASelection() {
        assertNull(PersonalCollection.target(context,PokemonScreenData(pokemonName="Unown")))
        val a = PersonalCollection.target(context,PokemonScreenData(pokemonName="Unown",unownLetter="A"))!!
        val b = PersonalCollection.target(context,PokemonScreenData(pokemonName="Unown",unownLetter="B"))!!
        assertNotEquals(a.key,b.key)
        assertNull(PersonalCollection.target(context,PokemonScreenData(pokemonName="Scatterbug")))
        val patterns = VivillonPattern.entries.take(2).map { PersonalCollection.target(context,PokemonScreenData(pokemonName="Scatterbug",vivillonPattern=it))!!.key }
        assertNotEquals(patterns[0],patterns[1])
    }
    @Test fun reviewRequiresOptInAndTurningItOffPreservesCollection() {
        val pokemon = target("Pikachu")
        PersonalCollection.mark(context,pokemon,Group.SHINY,true)
        assertFalse(PersonalCollection.read(context).showInSuggestedNames)
        PersonalCollection.setShowInSuggestedNames(context,true)
        assertTrue(PersonalCollection.read(context).showInSuggestedNames)
        PersonalCollection.setShowInSuggestedNames(context,false)
        val snapshot = PersonalCollection.read(context)
        assertFalse(snapshot.showInSuggestedNames)
        assertTrue(Group.SHINY in snapshot.enabled)
        assertEquals(setOf(Group.SHINY),snapshot.entries[pokemon.key]!!.groups)
    }

    @Test fun portraitsRespectRegionalFormsAndLycanrocVariants() {
        val images = com.mewname.app.domain.PersonalCollectionImages(context)
        val normal = images.url(target("Arcanine"))
        val hisui = images.url(target("Arcanine (Hisui)"))
        assertNotNull(normal)
        assertNotNull(hisui)
        assertNotEquals(normal, hisui)
        val wolves = listOf("Lycanroc (Diurno)", "Lycanroc (Noturno)", "Lycanroc (Crepúsculo)")
            .map { images.url(target(it)) }
        assertTrue(wolves.all { it != null })
        assertEquals(3, wolves.distinct().size)
    }

    @Test fun portraitsRespectVivillonPatternsAndTranslatedUniqueForms() {
        val images = com.mewname.app.domain.PersonalCollectionImages(context)
        fun unique(name: String, form: String) = PersonalCollection.target(context,
            PokemonScreenData(pokemonName = name, uniqueForm = form))!!
        val wash = images.url(unique("Rotom", "Lavagem"))
        val heat = images.url(unique("Rotom", "Calor"))
        assertNotNull(wash)
        assertNotNull(heat)
        assertNotEquals(wash, heat)
        val butterflies = listOf(VivillonPattern.GARDEN, VivillonPattern.MARINE).map { pattern ->
            images.url(PersonalCollection.target(context, PokemonScreenData(pokemonName = "Vivillon", vivillonPattern = pattern))!!)
        }
        assertTrue(butterflies.all { it != null })
        assertEquals(2, butterflies.distinct().size)
    }
    @Test fun megaAndGigamaxRequireTheExactEligibleSpeciesAndForm() {
        assertTrue(PersonalCollection.supports(target("Charizard"), Group.MEGA))
        assertTrue(PersonalCollection.supports(target("Charizard"), Group.GIGANTAMAX))
        assertTrue(PersonalCollection.supports(target("Slowbro"), Group.MEGA))
        assertFalse(PersonalCollection.supports(target("Slowbro (Galar)"), Group.MEGA))
        assertTrue(PersonalCollection.supports(target("Meowth"), Group.GIGANTAMAX))
        assertFalse(PersonalCollection.supports(target("Meowth (Alola)"), Group.GIGANTAMAX))
        assertFalse(PersonalCollection.supports(target("Charmander"), Group.GIGANTAMAX))
        assertTrue(PersonalCollection.supports(target("Falinks"), Group.MEGA))
        assertTrue(PersonalCollection.supports(target("Starmie"), Group.MEGA))
        assertFalse(PersonalCollection.supports(null, Group.MEGA))
        PersonalCollection.mark(context, target("Pidgey"), Group.MEGA, true)
        assertTrue(PersonalCollection.read(context).entries.isEmpty())
    }

    @Test fun transformationCollectionsPersistIndependently() {
        val pokemon = target("Charizard")
        listOf(Group.DYNAMAX, Group.GIGANTAMAX, Group.MEGA).forEach {
            PersonalCollection.mark(context, pokemon, it, true)
        }
        PersonalCollection.mark(context, pokemon, Group.GIGANTAMAX, false)
        assertEquals(setOf(Group.DYNAMAX, Group.MEGA), PersonalCollection.read(context).entries[pokemon.key]!!.groups)
    }

    @Test fun removedMasterCategoryDoesNotErasePercentagesOrOtherCaptures() {
        val pokemon = target("Pikachu")
        val entry = org.json.JSONObject().put("key", pokemon.key).put("label", pokemon.label)
            .put("groups", org.json.JSONArray(listOf("MASTER", "IV98", "SHINY")))
        PersonalCollection.preferences(context).edit()
            .putStringSet("groups", setOf("MASTER", "IV98", "SHINY"))
            .putString("entries", org.json.JSONArray().put(entry).toString()).commit()
        val snapshot = PersonalCollection.read(context)
        assertEquals(setOf(Group.IV98, Group.SHINY), snapshot.enabled)
        assertEquals(setOf(Group.IV98, Group.SHINY), snapshot.entries[pokemon.key]!!.groups)
        assertFalse(Group.entries.any { it.name == "MASTER" })
    }

    @Test fun gameCollectionIconsAreBundledAndDecodable() {
        listOf("great", "ultra", "master", "mega", "dynamax", "gigantamax", "xxs", "xxl", "little", "shiny", "lucky", "shadow", "purified").forEach { name ->
            context.assets.open("collection/$name.png").use { input ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                assertNotNull(name, bitmap)
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
                bitmap.recycle()
            }
        }
    }
    @Test fun collectionCategoryChoosesShinyOrUncostumedStandardPortraits() {
        val images = com.mewname.app.domain.PersonalCollectionImages(context)
        val pikachu = target("Pikachu")
        val standard = images.urls(pikachu, Group.LUCKY).single()
        assertTrue(standard.endsWith("/pm25.icon.png"))
        assertTrue(images.urls(pikachu, Group.SHINY).single().endsWith("/pm25.s.icon.png"))
        listOf(Group.XXS, Group.XXL, Group.IV100, Group.GREAT, Group.ULTRA, Group.MASTER_LEAGUE).forEach {
            assertEquals(listOf(standard), images.urls(pikachu, it))
        }
        val hisui = target("Arcanine (Hisui)")
        assertNotEquals(images.urls(hisui, Group.LUCKY), images.urls(hisui, Group.SHINY))
        assertNotEquals(images.urls(target("Arcanine"), Group.SHINY), images.urls(hisui, Group.SHINY))
    }

    @Test fun megaAndGigamaxUseTransformedPortraitsInsteadOfTheBasePokemon() {
        val images = com.mewname.app.domain.PersonalCollectionImages(context)
        val charizard = target("Charizard")
        assertEquals(2, images.urls(charizard, Group.MEGA).size)
        assertTrue(images.urls(charizard, Group.GIGANTAMAX).single().contains("fGIGANTAMAX"))
        assertFalse(images.urls(charizard, Group.LUCKY).single() in images.urls(charizard, Group.MEGA))
        com.mewname.app.domain.CollectionTransformations.mega.forEach { name ->
            assertTrue(name, images.urls(target(name), Group.MEGA).isNotEmpty())
        }
        com.mewname.app.domain.CollectionTransformations.gigantamax.forEach { name ->
            assertTrue(name, images.urls(target(name), Group.GIGANTAMAX).isNotEmpty())
        }
        assertTrue(images.urls(target("Meowth (Alola)"), Group.GIGANTAMAX).isEmpty())
        assertTrue(images.urls(target("Chesnaught"), Group.MEGA).single().endsWith("/pm652.fMEGA.icon.png"))
    }
}