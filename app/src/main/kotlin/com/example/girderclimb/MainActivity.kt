package com.example.girderclimb

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(GameView(this))
    }
}

class Barrel(var x: Float, var p: Int, var dir: Int)

class GameView(ctx: Context) : View(ctx) {
    private val W = 240f
    private val H = 320f
    private val baseY = floatArrayOf(300f, 246f, 192f, 138f, 84f)
    private val slope = floatArrayOf(0f, -0.06f, 0.06f, -0.06f, 0.06f)
    private val lx = floatArrayOf(190f, 50f, 180f, 100f) // ladder k joins platform k and k+1
    private fun platY(p: Int, x: Float) = baseY[p] + slope[p] * (x - 120f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barrels = mutableListOf<Barrel>()
    private var px = 20f; private var p = 0; private var jumpH = 0f; private var jumpV = 0f
    private var climbing = false; private var climbK = 0; private var cy = 0f
    private var lives = 3; private var score = 0; private var level = 0
    private var gameOver = false; private var spawnT = 1f; private var pauseT = 0f
    private var last = System.nanoTime()

    private var bL = false; private var bR = false; private var bU = false; private var bD = false; private var bJ = false
    private val btns = Array(5) { RectF() }
    private val labels = arrayOf("<", ">", "^", "v", "JUMP")

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        ghost = null
        val top = h * 0.75f; val bot = h * 0.98f; val m = w * 0.01f
        val xs = floatArrayOf(0.01f, 0.17f, 0.35f, 0.51f, 0.70f)
        val xe = floatArrayOf(0.16f, 0.32f, 0.50f, 0.66f, 0.99f)
        for (i in 0..4) btns[i].set(xs[i] * w + m, top, xe[i] * w - m, bot)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && gameOver) { restart(); return true }
        bL = false; bR = false; bU = false; bD = false; bJ = false
        val lifting = e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL
        for (i in 0 until e.pointerCount) {
            if (lifting) break
            if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
            val x = e.getX(i); val y = e.getY(i)
            if (btns[0].contains(x, y)) bL = true
            if (btns[1].contains(x, y)) bR = true
            if (btns[2].contains(x, y)) bU = true
            if (btns[3].contains(x, y)) bD = true
            if (btns[4].contains(x, y)) bJ = true
        }
        return true
    }

    private fun restart() { lives = 3; score = 0; level = 0; gameOver = false; resetRound() }
    private fun resetRound() {
        barrels.clear(); px = 20f; p = 0; jumpH = 0f; jumpV = 0f; climbing = false; spawnT = 1f; pauseT = 0f
    }
    private fun dirFor(pl: Int, cur: Int) = if (slope[pl] > 0) 1 else if (slope[pl] < 0) -1 else cur

    private fun update(dt: Float) {
        if (gameOver) return
        if (pauseT > 0f) { pauseT -= dt; if (pauseT <= 0f) resetRound(); return }

        if (climbing) {
            val top = platY(climbK + 1, lx[climbK]); val bot = platY(climbK, lx[climbK])
            if (bU) cy -= 50f * dt
            if (bD) cy += 50f * dt
            if (cy <= top) { p = climbK + 1; climbing = false; px = lx[climbK] }
            else if (cy >= bot) { p = climbK; climbing = false; px = lx[climbK] }
        } else {
            if (bL) px -= 55f * dt
            if (bR) px += 55f * dt
            px = px.coerceIn(4f, 236f)
            if (jumpH == 0f) {
                if (bJ) jumpV = 170f
                else if (bU && p < 4 && abs(px - lx[p]) < 7f) { climbing = true; climbK = p; px = lx[p]; cy = platY(p, px) }
                else if (bD && p > 0 && abs(px - lx[p - 1]) < 7f) { climbing = true; climbK = p - 1; px = lx[p - 1]; cy = platY(p, px) }
            }
            if (!climbing && (jumpH > 0f || jumpV > 0f)) {
                jumpV -= 520f * dt; jumpH += jumpV * dt
                if (jumpH <= 0f) { jumpH = 0f; jumpV = 0f }
            }
        }

        spawnT -= dt
        if (spawnT <= 0f) { barrels.add(Barrel(50f, 4, 1)); spawnT = max(0.9f, 2.2f - level * 0.2f) }

        val spd = 55f + level * 6f
        val it = barrels.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.dir * spd * dt
            if (b.p > 0 && Random.nextFloat() < 0.07f && abs(b.x - lx[b.p - 1]) < 2f) {
                b.p -= 1; b.x = lx[b.p]; b.dir = dirFor(b.p, b.dir)
            }
            if (b.x > W || b.x < 0f) {
                if (b.p == 0) { it.remove(); continue }
                b.p -= 1; b.x = b.x.coerceIn(0f, W); b.dir = dirFor(b.p, -b.dir)
            }
        }

        val py = if (climbing) cy else platY(p, px) - jumpH
        for (b in barrels) {
            val by = platY(b.p, b.x) - 5f
            if (hypot(px - b.x, py - 7f - by) < 9f) {
                lives--; if (lives <= 0) gameOver = true else pauseT = 1f
                return
            }
        }
        if (p == 4 && !climbing && px > 205f) { score += 500; level++; resetRound() }
    }

    // ---- LCD look ----
    private val LCD = Color.rgb(184, 192, 164)
    private val INK = Color.rgb(28, 30, 26)
    private val inkP = Paint().apply { color = INK; isAntiAlias = false }
    private val shadowP = Paint().apply { color = INK; alpha = 40; isAntiAlias = false }
    private val ghostP = Paint().apply { color = INK; alpha = 20; isAntiAlias = false }
    private var ghost: Bitmap? = null

    private val P0 = arrayOf("...####...", "..######..", "..#.##.#..", "..######..", "...####...", "..######..",
        ".########.", ".#.####.#.", "...#..#...", "...#..#...", "..##..##..", "..##..##..")
    private val P1 = P0.copyOf().also { it[8] = "..#....#.."; it[9] = ".##....##."; it[10] = ".##....##."; it[11] = ".##....##." }
    private val PC = P0.copyOf().also { it[5] = "#.######.#"; it[6] = "#.######.#"; it[7] = "..######.." }
    private val PC1 = PC.copyOf().also { it[8] = P1[8]; it[9] = P1[9]; it[10] = P1[10]; it[11] = P1[11] }
    private val BA = arrayOf(".######.", "########", "##.##.##", "########", "########", "##.##.##", "########", ".######.")
    private val BB = arrayOf(".######.", "########", "#.####.#", "########", "########", "#.####.#", "########", ".######.")
    private val BOSS = arrayOf("......#..#......", "......#..#......", "..############..", "..############..",
        "..##..####..##..", "..##..####..##..", "..############..", "..#.#.#..#.#.#..", "..############..",
        "#.############.#", "#.############.#", "#..##......##..#", "...##......##...", "...##......##...")
    private val ORB = arrayOf("...#...", "..###..", ".#####.", ".#####.", "#######", "..###..")

    private fun spr(c: Canvas, rows: Array<String>, cx: Float, bot: Float, u: Float, p: Paint) {
        val x0 = cx - rows[0].length * u / 2f; val y0 = bot - rows.size * u
        for (r in rows.indices) for (i in rows[r].indices)
            if (rows[r][i] == '#') c.drawRect(x0 + i * u, y0 + r * u, x0 + (i + 1) * u, y0 + (r + 1) * u, p)
    }
    private fun lit(c: Canvas, rows: Array<String>, cx: Float, bot: Float, u: Float) {
        spr(c, rows, cx + 0.8f, bot + 0.8f, u, shadowP); spr(c, rows, cx, bot, u, inkP)
    }
    private fun snap(x: Float) = (x / 8f).roundToInt() * 8f

    private fun buildGhost(ox: Float, oy: Float, s: Float): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val g = Canvas(bmp); g.translate(ox, oy); g.scale(s, s)
        for (pl in 0..4) {
            var x = 8f
            while (x < W) {
                spr(g, BA, x, platY(pl, x) - 2.5f, 1.2f, ghostP)
                spr(g, P0, x + 8f, platY(pl, x + 8f) - 2.5f, 1.3f, ghostP)
                x += 16f
            }
        }
        return bmp
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = min(0.033f, (now - last) / 1e9f); last = now
        update(dt)

        c.drawColor(Color.rgb(222, 214, 190)) // plastic case
        val sl = 10f; val st = 10f; val sr = width - 10f; val sb = height * 0.73f
        val s = min((sr - sl) / W, (sb - st) / H)
        val ox = sl + ((sr - sl) - W * s) / 2f; val oy = st + ((sb - st) - H * s) / 2f
        paint.style = Paint.Style.FILL; paint.color = LCD
        c.drawRoundRect(sl, st, sr, sb, 14f, 14f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.color = Color.rgb(120, 118, 105)
        c.drawRoundRect(sl, st, sr, sb, 14f, 14f, paint)
        if (ghost == null) ghost = buildGhost(ox, oy, s)
        c.drawBitmap(ghost!!, 0f, 0f, null)

        c.save(); c.translate(ox, oy); c.scale(s, s)
        paint.color = INK; paint.style = Paint.Style.STROKE
        for (k in 0..3) {
            var y = platY(k + 1, lx[k]) ; val yb = platY(k, lx[k])
            paint.strokeWidth = 1.3f
            c.drawLine(lx[k] - 4, y, lx[k] - 4, yb, paint); c.drawLine(lx[k] + 4, y, lx[k] + 4, yb, paint)
            while (y < yb) { c.drawLine(lx[k] - 4, y, lx[k] + 4, y, paint); y += 5f }
        }
        for (i in 0..4) {
            paint.strokeWidth = 3f; c.drawLine(0f, platY(i, 0f), W, platY(i, W), paint)
            paint.strokeWidth = 1f
            var x = 3f
            while (x < W) { c.drawLine(x, platY(i, x), x + 3f, platY(i, x) + 4f, paint); x += 6f }
        }
        paint.style = Paint.Style.FILL

        val bump = if (spawnT > 1.6f) -1.5f else 0f
        lit(c, BOSS, 27f, platY(4, 27f) - 2.5f + bump, 1.2f)
        lit(c, ORB, 215f, platY(4, 215f) - 2.5f, 1.5f)
        for (b in barrels) {
            val bx = snap(b.x)
            lit(c, if (((b.x / 8f).toInt() % 2) == 0) BA else BB, bx, platY(b.p, bx) - 2.5f, 1.2f)
        }

        val blink = pauseT > 0f && ((now / 120_000_000L) % 2L == 0L)
        if (!blink) {
            val frame = if (climbing) (if (((cy / 6f).toInt() % 2) == 0) PC else PC1)
                        else if (((px / 8f).toInt() % 2) == 0 || jumpH > 0f) P0 else P1
            val sx = if (climbing) lx[climbK] else snap(px)
            val sy = if (climbing) cy else platY(p, sx) - 2.5f - jumpH
            lit(c, frame, sx, sy, 1.3f)
        }

        paint.color = INK; paint.typeface = Typeface.MONOSPACE; paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.LEFT; paint.textSize = 13f
        c.drawText(String.format("%05d", score), 6f, 15f, paint)
        for (i in 0 until lives) lit(c, P0, W - 10f - i * 11f, 22f, 0.8f)
        paint.textAlign = Paint.Align.CENTER
        if (pauseT > 0f) { paint.textSize = 16f; c.drawText("MISS", W / 2, 160f, paint) }
        if (gameOver) {
            paint.textSize = 22f; c.drawText("GAME OVER", W / 2, 150f, paint)
            paint.textSize = 11f; c.drawText("TAP TO PLAY", W / 2, 170f, paint)
        }
        paint.isFakeBoldText = false
        c.restore()

        val pressed = booleanArrayOf(bL, bR, bU, bD, bJ)
        paint.textAlign = Paint.Align.CENTER; paint.textSize = height * 0.04f
        for (i in 0..4) {
            paint.style = Paint.Style.FILL
            paint.color = if (i == 4) (if (pressed[i]) Color.rgb(130, 30, 30) else Color.rgb(176, 52, 48))
                          else (if (pressed[i]) Color.rgb(25, 28, 40) else Color.rgb(58, 62, 82))
            c.drawRoundRect(btns[i], 28f, 28f, paint)
            paint.color = Color.rgb(222, 214, 190)
            c.drawText(labels[i], btns[i].centerX(), btns[i].centerY() + paint.textSize / 3f, paint)
        }
        postInvalidateOnAnimation()
    }
}
