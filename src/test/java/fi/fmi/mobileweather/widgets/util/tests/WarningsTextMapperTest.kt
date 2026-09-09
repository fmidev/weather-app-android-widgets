package fi.fmi.mobileweather.widgets.util

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class WarningsTextMapperTest {
    @Test fun unknownAndMissingTypesResolveToLocalizedFallbackText() {
        val context = RuntimeEnvironment.getApplication()
        val translations = mapOf(
            "fi" to "Tuntematon varoitus",
            "sv" to "Okänd varning",
            "en" to "Unknown warning",
            "de" to "Unknown warning"
        )
        for ((language, expected) in translations) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localizedContext = context.createConfigurationContext(configuration)
            for (type in listOf(null, "", "newWarningType")) {
                assertEquals(expected, localizedContext.getString(WarningsTextMapper.getStringResourceId(type)))
            }
        }
    }
}
