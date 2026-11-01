package meow.ancient.client.util.other

import meow.ancient.Ancient
import java.io.InputStream

fun fromAssets(path: String): InputStream? {
    return Ancient::class.java.classLoader.getResourceAsStream(path)
}
