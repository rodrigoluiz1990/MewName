package com.mewname.app

import com.mewname.app.domain.isNewerRelease
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumbersInsteadOfTextOrTagInequality() {
        assertTrue(isNewerRelease("v1.0.9", "v1.0.10"))
        assertFalse(isNewerRelease("v1.0.29", "v1.0.28"))
        assertFalse(isNewerRelease("v2.0.0", "v1.99.99"))
        assertTrue(isNewerRelease("1.9.99", "2.0.0"))
    }

    @Test fun normalizesTagsAndIgnoresBuildMetadata() {
        assertFalse(isNewerRelease("refs/tags/v1.2.0", "1.2"))
        assertFalse(isNewerRelease("V1.2.3+local", "v1.2.3+release"))
        assertTrue(isNewerRelease("1.0.29", " refs/tags/v1.0.30 "))
    }

    @Test fun ordersPrereleasesBeforeStableAndComparesNumericIdentifiers() {
        assertTrue(isNewerRelease("1.2.3-rc.2", "1.2.3-rc.10"))
        assertTrue(isNewerRelease("1.2.3-rc.10", "1.2.3"))
        assertFalse(isNewerRelease("1.2.3", "1.2.3-rc.10"))
        assertTrue(isNewerRelease("1.2.3-alpha", "1.2.3-beta"))
        assertTrue(isNewerRelease("1.2.3-alpha", "1.2.3-alpha.1"))
        assertTrue(isNewerRelease("1.2.3-1", "1.2.3-alpha"))
    }

    @Test fun unknownOrMalformedVersionsDoNotRecommendAnUpdate() {
        for (tag in listOf("dev", "desconhecida", "", "1.2.3.4", "1.2.3-", "1.2.x")) {
            assertFalse(isNewerRelease(tag, "1.2.3"))
            assertFalse(isNewerRelease("1.2.3", tag))
        }
    }
}
