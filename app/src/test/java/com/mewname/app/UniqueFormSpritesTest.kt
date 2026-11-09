package com.mewname.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.VivillonPattern
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class UniqueFormSpritesTest {
 @Test fun namingFormsResolveDistinctSpritesIncludingVivillon() {
  val images = PersonalCollectionImages(ApplicationProvider.getApplicationContext<Context>())
  val groups = UniquePokemonCatalog.allSpecs().map { spec -> spec.pokemonNames.first() to spec.options.map { it.label } } +
   listOf("VIVILLON" to VivillonPattern.entries.map { it.name })
  for ((name, variants) in groups) {
   val urls = variants.map { variant ->
    val target = PersonalCollectionTarget(JSONArray(listOf(name,variant)).toString(), "$name $variant")
    images.url(target).also { assertNotNull(target.label,it) }
   }
   assertEquals(name,variants.size,urls.distinct().size)
  }
 }
}
