package com.Azhe.ghs.gshschedule.settings.provider

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.graphics.ColorUtils
import splitties.dimensions.dip
import splitties.resources.styledColor

/**
 * GKD 风格的设置卡片样式工具：
 * 每个设置项是一张圆角浅灰卡片（深色模式为深灰），带波纹点击反馈，无描边无阴影。
 * 颜色按当前日夜模式自动选择，无需 values-night 资源。
 */
internal object SettingCardStyle {

    /** 卡片左右外边距 */
    fun marginH(context: Context): Int = context.dip(12)

    /** 卡片纵向间距（单边） */
    fun gapV(context: Context): Int = context.dip(4)

    /** 卡片内文字距屏幕边缘的距离 = marginH + 卡片内 padding，供分组标题对齐用 */
    fun contentInset(context: Context): Int = marginH(context) + context.dip(16)

    /** 卡片背景色：浅色模式比页面底色略深的浅灰，深色模式比页面底色略亮的深灰 */
    fun cardColor(context: Context): Int {
        val ta = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        val bg = ta.getColor(0, Color.WHITE)
        ta.recycle()
        return if (ColorUtils.calculateLuminance(bg) > 0.5) {
            0xFFF1F1F4.toInt()
        } else {
            0xFF26262B.toInt()
        }
    }

    /** 带波纹点击反馈的圆角卡片背景 */
    fun cardBackground(context: Context): Drawable {
        val drawable = GradientDrawable().apply {
            cornerRadius = context.dip(20).toFloat()
            setColor(cardColor(context))
        }
        return RippleDrawable(
                ColorStateList.valueOf(context.styledColor(android.R.attr.colorControlHighlight)),
                drawable,
                drawable
        )
    }
}
