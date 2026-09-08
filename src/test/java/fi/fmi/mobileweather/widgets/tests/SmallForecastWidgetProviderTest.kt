package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallForecastWidgetProviderTest {
    private lateinit var context: Context
    private lateinit var manager: AppWidgetManager
    private val widgetId = 1

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).bindAppWidgetId(widgetId, ComponentName(context, SmallForecastWidgetProvider::class.java))
    }

    @Test fun symbolAndTemperatureShrinkToFitASingleRowWidget() {
        val root = render()
        measureAndLayout(root, 180, 110)
        val icon = root.findViewById<ImageView>(R.id.weatherIconImageView)
        val temperature = root.findViewById<TextView>(R.id.temperatureTextView)
        val originalIconHeight = icon.height
        val originalTextSize = temperature.textSize

        measureAndLayout(root, 180, 78)

        assertTrue(icon.height < originalIconHeight)
        assertTrue(temperature.textSize < originalTextSize)
        assertWeatherFits(root)
    }

    @Test fun temperatureAndDegreeRemainVisibleWithNarrowWidthAndLargeFont() {
        context = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            fontScale = 1.5f
        })
        val root = render(temperature = -32.0)

        measureAndLayout(root, 140, 86)

        assertEquals("-32°", root.findViewById<TextView>(R.id.temperatureTextView).text.toString())
        assertWeatherFits(root)
    }

    @Test fun resizingBackRestoresSymbolAndTemperatureSizes() {
        val root = render()
        measureAndLayout(root, 180, 110)
        val icon = root.findViewById<ImageView>(R.id.weatherIconImageView)
        val temperature = root.findViewById<TextView>(R.id.temperatureTextView)
        val originalIconHeight = icon.height
        val originalTextSize = temperature.textSize

        measureAndLayout(root, 180, 78)
        measureAndLayout(root, 180, 110)

        assertEquals(originalIconHeight, icon.height)
        assertEquals(originalTextSize, temperature.textSize, 0.01f)
        assertWeatherFits(root)
    }

    @Test fun otherSmallWidgetLayoutsKeepTheirSeparateTemperatureUnit() {
        for (layout in listOf(R.layout.xs_forecast_widget_layout, R.layout.horizontal_forecast_widget_layout)) {
            val root = render(layout = layout)
            assertEquals("12", root.findViewById<TextView>(R.id.temperatureTextView).text.toString())
            assertEquals("°", root.findViewById<TextView>(R.id.temperatureUnitTextView).text.toString())
        }
    }

    private fun render(temperature: Double = 12.0, layout: Int = R.layout.small_forecast_widget_layout): ViewGroup {
        val data = WidgetData(forecast = listOf(ForecastItem(
            epochtime = System.currentTimeMillis() / 1000 + 3600,
            localtime = "20260908T120000",
            name = "Helsinki",
            region = "Uusimaa",
            temperature = temperature,
            smartSymbol = 32
        )))
        val views = RemoteViews(context.packageName, layout)
        TestProvider().render(context, manager, data, views, widgetId)
        return views.apply(context, null) as ViewGroup
    }

    private fun measureAndLayout(root: View, width: Int, height: Int) {
        // Auto-sizing text can request a second layout after choosing its font size.
        repeat(2) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, width, height)
        }
    }

    private fun assertWeatherFits(root: ViewGroup) {
        for (id in listOf(R.id.weatherIconImageView, R.id.temperatureTextView)) {
            val child = root.findViewById<View>(id)
            val bounds = Rect(0, 0, child.width, child.height)
            root.offsetDescendantRectToMyCoords(child, bounds)
            assertTrue("Weather content must have visible dimensions", bounds.width() > 0 && bounds.height() > 0)
            assertTrue(bounds.left >= root.paddingLeft)
            assertTrue(bounds.right <= root.width - root.paddingRight)
            assertTrue(bounds.top >= root.paddingTop)
            assertTrue("Weather content must fit above the bottom padding", bounds.bottom <= root.height - root.paddingBottom)
        }
        val temperature = root.findViewById<TextView>(R.id.temperatureTextView)
        val textLayout = temperature.layout
        assertEquals(1, textLayout.lineCount)
        assertEquals(temperature.text.length, textLayout.getLineEnd(0))
        assertTrue("Temperature glyphs must fit vertically", textLayout.height <= temperature.height)
        assertTrue("Temperature glyphs must fit horizontally", textLayout.getLineWidth(0) <= temperature.width)
    }

    private class TestProvider : SmallForecastWidgetProvider() {
        fun render(context: Context, manager: AppWidgetManager, data: WidgetData, views: RemoteViews, widgetId: Int) {
            setWidgetUi(context, manager, data, SharedPreferencesHelper.getInstance(context, widgetId),
                WidgetInitResult(views, false), widgetId)
        }
    }
}
