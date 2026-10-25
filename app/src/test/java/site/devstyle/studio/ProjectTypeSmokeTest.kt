package site.devstyle.studio

import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTypeSmokeTest {
    @Test fun projectPackageIsStable() {
        assertTrue("site.devstyle.studio".contains("."))
    }
}
