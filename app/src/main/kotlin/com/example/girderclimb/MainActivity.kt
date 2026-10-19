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

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = min(0.033f, (now - last) / 1e9f); last = now
        update(dt)

        c.drawColor(Color.rgb(15, 15, 35))
        val s = min(width / W, height * 0.72f / H)
        c.save(); c.translate((width - W * s) / 2f, 0f); c.scale(s, s)

        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = Color.rgb(120, 200, 255)
        for (k in 0..3) {
            var y = platY(k + 1, lx[k]); val yb = platY(k, lx[k])
            c.drawLine(lx[k] - 4, y, lx[k] - 4, yb, paint); c.drawLine(lx[k] + 4, y, lx[k] + 4, yb, paint)
            while (y < yb) { c.drawLine(lx[k] - 4, y, lx[k] + 4, y, paint); y += 5f }
        }
        paint.strokeWidth = 5f; paint.color = Color.rgb(220, 80, 60)
        for (i in 0..4) c.drawLine(0f, platY(i, 0f), W, platY(i, W), paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(140, 90, 50); c.drawRect(15f, platY(4, 25f) - 26f, 39f, platY(4, 25f) - 3f, paint) // boss
        paint.color = Color.WHITE; c.drawRect(21f, platY(4, 25f) - 20f, 25f, platY(4, 25f) - 15f, paint); c.drawRect(30f, platY(4, 25f) - 20f, 34f, platY(4, 25f) - 15f, paint)
        paint.color = Color.rgb(255, 210, 60); c.drawCircle(215f, platY(4, 215f) - 10f, 6f, paint) // goal
        paint.color = Color.rgb(255, 160, 40)
        for (b in barrels) c.drawCircle(b.x, platY(b.p, b.x) - 5f, 5f, paint)

        val py = if (climbing) cy else platY(p, px) - jumpH
        paint.color = if (pauseT > 0f) Color.GRAY else Color.rgb(90, 220, 120)
        c.drawRect(px - 5f, py - 14f, px + 5f, py, paint)
        paint.color = Color.WHITE; c.drawRect(px - 3f, py - 12f, px + 3f, py - 8f, paint)

        paint.color = Color.WHITE; paint.textSize = 12f; paint.textAlign = Paint.Align.LEFT
        c.drawText("SCORE $score   LIVES $lives   LVL ${level + 1}", 6f, 14f, paint)
        if (gameOver) {
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 24f
            c.drawText("GAME OVER", W / 2, 150f, paint)
            paint.textSize = 12f; c.drawText("tap to restart", W / 2, 170f, paint)
        }
        c.restore()

        val pressed = booleanArrayOf(bL, bR, bU, bD, bJ)
        paint.textAlign = Paint.Align.CENTER; paint.textSize = height * 0.04f
        for (i in 0..4) {
            paint.color = if (pressed[i]) Color.rgb(90, 110, 180) else Color.rgb(45, 50, 90)
            c.drawRoundRect(btns[i], 20f, 20f, paint)
            paint.color = Color.WHITE
            c.drawText(labels[i], btns[i].centerX(), btns[i].centerY() + paint.textSize / 3f, paint)
        }
        postInvalidateOnAnimation()
    }
}
