package meow.ancient.client.util.render.font

import meow.ancient.client.extensions.CLIENT_ID
import java.util.concurrent.ConcurrentHashMap

object Fonts {
    private val map = ConcurrentHashMap<String, Font>()

    @JvmStatic
    fun get(name: String, path: String): Font = map.computeIfAbsent(name) {
        val full = "assets/$CLIENT_ID/$path"
        FontLoader.load(name, "$full.json", "$full.png")
    }

    @JvmStatic val google_sans_regular: Font by lazy { get("google_sans_regular", "fonts/g_sans/google_sans_regular") }
    @JvmStatic val google_sans_medium: Font by lazy { get("google_sans_medium", "fonts/g_sans/google_sans_medium") }
    @JvmStatic val google_sans_semi: Font by lazy { get("google_sans_semi", "fonts/g_sans/google_sans_semi") }
    @JvmStatic val google_sans_bold: Font by lazy { get("google_sans_bold", "fonts/g_sans/google_sans_bold") }
    @JvmStatic val google_sans_flex: Font get() = google_sans_regular

    @JvmStatic val product_sans_black: Font by lazy { get("product_sans_black", "fonts/p_sans/product_sans_black") }
    @JvmStatic val product_sans_bold: Font by lazy { get("product_sans_bold", "fonts/p_sans/product_sans_bold") }
    @JvmStatic val product_sans_medium: Font by lazy { get("product_sans_medium", "fonts/p_sans/product_sans_medium") }
    @JvmStatic val product_sans_regular: Font by lazy { get("product_sans_regular", "fonts/p_sans/product_sans_regular") }
    @JvmStatic val product_sans_light: Font by lazy { get("product_sans_light", "fonts/p_sans/product_sans_light") }
    @JvmStatic val product_sans_thin: Font by lazy { get("product_sans_thin", "fonts/p_sans/product_sans_thin") }

    @JvmStatic val default: Font get() = google_sans_regular

    @JvmStatic
    fun preload() {
        google_sans_regular
        google_sans_medium
        product_sans_regular
    }
}