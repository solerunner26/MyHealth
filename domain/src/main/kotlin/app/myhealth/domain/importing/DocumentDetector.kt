package app.myhealth.domain.importing

import kotlin.math.abs
import kotlin.math.hypot

/** A point in normalised image coordinates (0..1). */
data class Pt(val x: Float, val y: Float)

/** Page corners, clockwise from top-left, in normalised coordinates. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val points get() = listOf(tl, tr, br, bl)
    /** Shoelace area in normalised units (0..1). */
    val area: Float get() {
        val p = points; var s = 0f
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % 4]; s += a.x * b.y - b.x * a.y }
        return abs(s) / 2f
    }
    /** Output size (pixels) that keeps the page's proportions for an image of [w] × [h]. */
    fun outputSize(w: Int, h: Int, maxEdge: Int = 2400): Pair<Int, Int> {
        fun d(a: Pt, b: Pt) = hypot((a.x - b.x) * w, (a.y - b.y) * h)
        var ow = maxOf(d(tl, tr), d(bl, br)); var oh = maxOf(d(tl, bl), d(tr, br))
        val k = minOf(1f, maxEdge / maxOf(ow, oh)); ow *= k; oh *= k
        return ow.toInt().coerceAtLeast(1) to oh.toInt().coerceAtLeast(1)
    }
}

/**
 * Item 5: finds a sheet of paper in a photo, offline and without libraries. A report page is the large bright region
 * on a darker background: Otsu threshold → largest bright connected region → its four extreme corners. Returns null
 * when not confident (page fills the frame, no clear page, odd shape), so the caller keeps the original photo.
 */
object DocumentDetector {
    /** [lum] is a small greyscale image (0..255), row-major, [w] × [h]; ~300 px on the long edge is plenty. */
    fun detect(lum: IntArray, w: Int, h: Int): Quad? {
        require(lum.size == w * h && w > 8 && h > 8)
        val t = otsu(lum)
        val bright = BooleanArray(lum.size) { lum[it] > t }
        // largest 4-connected bright component (iterative flood fill)
        val label = IntArray(lum.size); var best = 0; var bestSize = 0; var next = 1
        val stack = IntArray(lum.size)
        for (start in lum.indices) {
            if (!bright[start] || label[start] != 0) continue
            var sp = 0; stack[sp++] = start; label[start] = next; var size = 0
            while (sp > 0) {
                val i = stack[--sp]; size++
                val x = i % w; val y = i / w
                if (x > 0 && bright[i - 1] && label[i - 1] == 0) { label[i - 1] = next; stack[sp++] = i - 1 }
                if (x < w - 1 && bright[i + 1] && label[i + 1] == 0) { label[i + 1] = next; stack[sp++] = i + 1 }
                if (y > 0 && bright[i - w] && label[i - w] == 0) { label[i - w] = next; stack[sp++] = i - w }
                if (y < h - 1 && bright[i + w] && label[i + w] == 0) { label[i + w] = next; stack[sp++] = i + w }
            }
            if (size > bestSize) { bestSize = size; best = next }
            next++
        }
        val fill = bestSize.toFloat() / lum.size
        if (fill < 0.15f || fill > 0.97f) return null
        var tl = 0; var tr = 0; var br = 0; var bl = 0
        var sTl = Int.MAX_VALUE; var sTr = Int.MIN_VALUE; var sBr = Int.MIN_VALUE; var sBl = Int.MIN_VALUE
        for (i in lum.indices) {
            if (label[i] != best) continue
            val x = i % w; val y = i / w
            if (x + y < sTl) { sTl = x + y; tl = i }
            if (x + y > sBr) { sBr = x + y; br = i }
            if (x - y > sTr) { sTr = x - y; tr = i }
            if (y - x > sBl) { sBl = y - x; bl = i }
        }
        fun p(i: Int) = Pt((i % w + 0.5f) / w, (i / w + 0.5f) / h)
        val q = Quad(p(tl), p(tr), p(br), p(bl))
        // a page should be a reasonably large, convex four-sided shape that is not just the whole frame
        if (q.area < 0.15f || q.area > 0.96f || !convex(q)) return null
        return q
    }

    private fun convex(q: Quad): Boolean {
        val p = q.points
        val signs = p.indices.map { i ->
            val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return signs.all { it > 0 } || signs.all { it < 0 }
    }

    fun otsu(lum: IntArray): Int {
        val hist = IntArray(256); lum.forEach { hist[it.coerceIn(0, 255)]++ }
        val total = lum.size.toDouble(); var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var bestT = 127; var bestVar = -1.0
        for (t in 0..255) {
            wB += hist[t]; if (wB == 0.0) continue
            val wF = total - wB; if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val v = wB * wF * (mB - mF) * (mB - mF)
            if (v > bestVar) { bestVar = v; bestT = t }
        }
        return bestT
    }
}
