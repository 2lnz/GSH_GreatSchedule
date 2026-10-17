package com.Azhe.ghs.gshschedule.settings.provider

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.settings.items.BaseSettingItem
import com.Azhe.ghs.gshschedule.settings.items.SettingType
import com.Azhe.ghs.gshschedule.settings.items.VerticalItem
import com.Azhe.ghs.gshschedule.utils.ViewUtils
import splitties.dimensions.dip
import splitties.resources.styledColor

class VerticalItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.VERTICAL

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LinearLayoutCompat(parent.context).apply {
            id = R.id.anko_layout
            orientation = LinearLayoutCompat.VERTICAL
            background = SettingCardStyle.cardBackground(context)
            setPadding(0, dip(16), 0, dip(16))

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_text_view
                textSize = 16f
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dip(16)
                marginEnd = dip(16)
            })

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_tv_description
                textSize = 12f
                setTextColor(styledColor(R.attr.colorOnSurfaceVariant))
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dip(4)
                marginStart = dip(16)
                marginEnd = dip(16)
            })
        }
        view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = SettingCardStyle.marginH(view.context)
            marginEnd = SettingCardStyle.marginH(view.context)
            topMargin = SettingCardStyle.gapV(view.context)
            bottomMargin = SettingCardStyle.gapV(view.context)
        }
        return BaseViewHolder(view)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as VerticalItem

        // 标题为空的条目是占位空白（如列表尾部的 Spacer），不渲染成卡片
        if (item.title.isEmpty()) {
            helper.itemView.background = null
            (helper.itemView.layoutParams as? MarginLayoutParams)?.setMargins(0, 0, 0, 0)
            helper.itemView.requestLayout()
            helper.setGone(R.id.anko_text_view, true)
        } else {
            helper.itemView.background = SettingCardStyle.cardBackground(helper.itemView.context)
            (helper.itemView.layoutParams as? MarginLayoutParams)?.let {
                it.marginStart = SettingCardStyle.marginH(helper.itemView.context)
                it.marginEnd = SettingCardStyle.marginH(helper.itemView.context)
                it.topMargin = SettingCardStyle.gapV(helper.itemView.context)
                it.bottomMargin = SettingCardStyle.gapV(helper.itemView.context)
            }
            helper.itemView.requestLayout()
            helper.setVisible(R.id.anko_text_view, true)
        }

        helper.setText(R.id.anko_text_view, item.title)
        val desc = helper.getView<AppCompatTextView>(R.id.anko_tv_description)
        if (item.description.isEmpty()) {
            desc.visibility = View.GONE
        } else {
            desc.visibility = View.VISIBLE
            if (item.isSpanned) {
                desc.text = ViewUtils.getHtmlSpannedString(item.description)
            } else {
                desc.text = item.description
            }
        }
    }

}
