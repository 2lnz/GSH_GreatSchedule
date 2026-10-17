package com.Azhe.ghs.gshschedule.settings.provider

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.settings.items.BaseSettingItem
import com.Azhe.ghs.gshschedule.settings.items.HorizontalItem
import com.Azhe.ghs.gshschedule.settings.items.SettingType
import splitties.dimensions.dip
import splitties.resources.styledColor

class HorizontalItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.HORIZON

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LinearLayoutCompat(parent.context).apply {
            id = R.id.anko_layout
            background = SettingCardStyle.cardBackground(context)

            gravity = Gravity.CENTER_VERTICAL

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_text_view
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setLines(1)
            }, LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dip(16)
                marginEnd = dip(16)
                weight = 1f
            })

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_tv_value
                gravity = Gravity.CENTER_VERTICAL
                textSize = 12f
                setTextColor(styledColor(R.attr.colorOnSurfaceVariant))
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dip(16)
                marginEnd = dip(16)
            })
        }
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, view.dip(68)).apply {
            marginStart = SettingCardStyle.marginH(view.context)
            marginEnd = SettingCardStyle.marginH(view.context)
            topMargin = SettingCardStyle.gapV(view.context)
            bottomMargin = SettingCardStyle.gapV(view.context)
        }
        return BaseViewHolder(view)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as HorizontalItem
        helper.setText(R.id.anko_text_view, item.title)
        helper.setText(R.id.anko_tv_value, item.value)
    }


}
