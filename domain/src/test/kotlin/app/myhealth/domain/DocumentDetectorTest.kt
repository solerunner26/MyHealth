package app.myhealth.domain

import app.myhealth.domain.importing.DocumentDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DocumentDetectorTest {
    /** Dark desk, bright page rotated by [deg] around the centre, with some printed (dark) text lines inside. */
    private fun scene(w: Int, h: Int, deg: Double, pw: Double, ph: Double): IntArray {
        val a = Math.toRadians(deg); val cx = w / 2.0; val cy = h / 2.0
        return IntArray(w * h) { i ->
            val x = i % w - cx; val y = i / w - cy
            val u = x * cos(a) + y * sin(a); val v = -x * sin(a) + y * cos(a)
            val inside = kotlin.math.abs(u) < pw / 2 && kotlin.math.abs(v) < ph / 2
            when {
                !inside -> 40 + (i * 7919) % 25 // textured dark background
                (v.toInt() % 12 == 0) && kotlin.math.abs(u) < pw / 3 -> 70 // a text line
                else -> 225
            }
        }
    }

    @Test fun findsATiltedPageAndItsCorners() {
        val w = 300; val h = 400
        val q = assertNotNullAnd(DocumentDetector.detect(scene(w, h, 8.0, 200.0, 280.0), w, h))
        // the page is ~ 47 % of the frame; corners in the right quadrants
        assertEquals(0.47, q.area.toDouble(), 0.06)
        assert(q.tl.x < 0.5 && q.tl.y < 0.5 && q.br.x > 0.5 && q.br.y > 0.5)
        assert(q.tr.x > 0.5 && q.tr.y < 0.5 && q.bl.x < 0.5 && q.bl.y > 0.5)
        val (ow, oh) = q.outputSize(3000, 4000)
        assertEquals(200.0 / 280.0, ow.toDouble() / oh, 0.08)
    }

    @Test fun notConfidentMeansNoCrop() {
        val w = 200; val h = 200
        assertNull("page fills the frame", DocumentDetector.detect(IntArray(w * h) { 220 }, w, h))
        assertNull("nothing bright", DocumentDetector.detect(IntArray(w * h) { 30 + it % 7 }, w, h))
        assertNull("tiny bright spot", DocumentDetector.detect(scene(w, h, 0.0, 30.0, 30.0), w, h))
    }

    private fun <T> assertNotNullAnd(v: T?): T { assertNotNull(v); return v!! }
}
