package com.scin.androidblelibrary.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.scin.androidblelibrary.R
import com.scin.androidblelibrary.bean.MsgDataBean

class MsgAdapter:BaseQuickAdapter<MsgDataBean,BaseViewHolder>(R.layout.item_msg) {
    override fun convert(holder: BaseViewHolder, item: MsgDataBean) {
        if (item.isSend) {
            holder.setText(R.id.tvData,"发送：${item.data}")
            holder.itemView.setBackgroundColor(0xffeeeeee.toInt())
        } else {
            holder.setText(R.id.tvData,"接收：${item.data}")
            holder.itemView.setBackgroundColor(0xffffffff.toInt())
        }
    }
}