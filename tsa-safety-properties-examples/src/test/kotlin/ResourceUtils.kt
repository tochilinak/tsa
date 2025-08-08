import org.usvm.machine.getResourcePath
import java.nio.file.Path
import kotlin.io.path.Path

const val FIFT_STDLIB_PATH = "/fiftstdlib"

val FIFT_STDLIB_RESOURCE: Path = getResourcePath(object {}.javaClass, FIFT_STDLIB_PATH)
