package com.provacor.sathi.core

import com.provacor.sathi.core.text.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test fun digitsCaseAndSpaces() = assertEquals("class 10 physics", TextNormalizer.normalize("  Class   ১০ PHYSICS "))
    @Test fun chandrabinduDropped() = assertEquals(TextNormalizer.normalize("খুজো"), TextNormalizer.normalize("খুঁজো"))
    @Test fun suffixes() {
        assertEquals("ইউটিউব", TextNormalizer.stripBengaliSuffix("ইউটিউবটা"))
        assertEquals("ইউটিউব", TextNormalizer.stripBengaliSuffix("ইউটিউবে", includeLocative = true))
        assertEquals("youtube", TextNormalizer.stripBengaliSuffix("youtube-এ"))
    }
    @Test fun redaction() = assertEquals("OTP is ••••", TextNormalizer.redactForLog("OTP is 482913"))
}
