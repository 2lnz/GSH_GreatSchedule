package com.Azhe.ghs.gshschedule.settings.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.getPrefer
import splitties.dimensions.dip
import splitties.resources.color

/**
 * 仿 Uiverse 风格的圆角开关：轨道为胶囊形，滑块为白色圆形，
 * 开启时轨道颜色跟随主题颜色（KEY_THEME_COLOR），关闭时为灰色。
 * 行为（点击切换、performClick、isChecked、滑动动画）继承自 [SwitchCompat]。
 */
class ThemeSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.switchStyle
) : SwitchCompat(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x22000000
    }

    /** 开启时轨道颜色，跟随主题颜色 */
    private val trackColorOn = context.getPrefer().getInt(Const.KEY_THEME_COLOR, context.color(R.color.colorAccent))
    /** 关闭时轨道颜色 */
    private val trackColorOff = Color.parseColor("#cccccc")

    init {
        // 禁用默认轨道/滑块/文字，完全由 onDraw 绘制
        showText = false
        trackDrawable = null
        thumbDrawable = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
                resolveSize(dip(40), widthMeasureSpec),
                resolveSize(dip(22), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val knobD = dip(16).toFloat()
        val inset = (h - knobD) / 2f

        // 轨道：胶囊形，开=主题色，关=灰
        trackPaint.color = if (isChecked) trackColorOn else trackColorOff
        canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, trackPaint)

        // 滑块位置（0=关在左，1=开在右）
        val position = getThumbPosition()
        val travel = w - knobD - inset * 2f
        val knobLeft = inset + position * travel
        val cx = knobLeft + knobD / 2f
        val cy = h / 2f

        // 软阴影 + 白色滑块
        val shadowOffset = dip(1).toFloat()
        canvas.drawCircle(cx + shadowOffset, cy + shadowOffset, knobD / 2f + shadowOffset, shadowPaint)
        canvas.drawCircle(cx, cy, knobD / 2f, thumbPaint)
    }
}
