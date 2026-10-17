package com.Azhe.ghs.gshschedule.settings.provider

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.settings.items.BaseSettingItem
import com.Azhe.ghs.gshschedule.settings.items.SettingType
import com.Azhe.ghs.gshschedule.settings.items.SwitchItem
import com.Azhe.ghs.gshschedule.settings.widget.ThemeSwitch
import splitties.dimensions.dip
import splitties.resources.styledColor

class SwitchItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.SWITCH

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = ConstraintLayout(context).apply {
            id = R.id.anko_layout
            background = SettingCardStyle.cardBackground(context)
            setPadding(dip(16), dip(16), 0, dip(16))

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_text_view
                textSize = 16f
            }, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                startToStart = ConstraintSet.PARENT_ID
                endToStart = R.id.anko_check_box
                topToTop = ConstraintSet.PARENT_ID
                bottomToTop = R.id.anko_tv_description
                marginEnd = dip(16)
            })

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_tv_description
                textSize = 12f
                setTextColor(styledColor(R.attr.colorOnSurfaceVariant))
            }, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dip(4)
                startToStart = ConstraintSet.PARENT_ID
                endToStart = R.id.anko_check_box
                topToBottom = R.id.anko_text_view
                bottomToBottom = ConstraintSet.PARENT_ID
                marginEnd = dip(16)
            })

            val checkBox = ThemeSwitch(context).apply {
                id = R.id.anko_check_box
            }

            addView(checkBox, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, dip(22)).apply {
                endToEnd = ConstraintSet.PARENT_ID
                topToTop = ConstraintSet.PARENT_ID
                bottomToBottom = ConstraintSet.PARENT_ID
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
        val item = data as SwitchItem
        helper.setText(R.id.anko_text_view, item.title)
        helper.getView<ThemeSwitch>(R.id.anko_check_box).apply {
            isChecked = item.checked
        }
        if (data.desc.isEmpty()) {
            helper.setGone(R.id.anko_tv_description, true)
        } else {
            helper.setText(R.id.anko_tv_description, item.desc)
            helper.setGone(R.id.anko_tv_description, false)
        }

    }

}
