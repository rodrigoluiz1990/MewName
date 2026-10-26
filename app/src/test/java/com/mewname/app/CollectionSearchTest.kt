package com.mewname.app
import org.junit.Test
import org.junit.Assert.*
class CollectionSearchTest {
 @Test fun typedPrefixesAndExactNamesRepositionIndependently() {
  val rows=listOf(4 to "Charmander",5 to "Charmeleon",6 to "Charizard",150 to "Mewtwo",151 to "Mew")
  for (query in listOf("c","ch","char")) assertEquals(0,collectionSearchIndex(query,rows))
  assertEquals(2,collectionSearchIndex("chari",rows))
  assertEquals(4,collectionSearchIndex("mew",rows))
  assertEquals(3,collectionSearchIndex("mewt",rows))
  assertEquals(4,collectionSearchIndex("151",rows))
  assertEquals(-1,collectionSearchIndex("absent",rows))
  assertEquals(-1,collectionSearchIndex("",rows))
  assertEquals(0,collectionSearchIndex("flabebe",listOf(669 to "Flabébé")))
 }
}
