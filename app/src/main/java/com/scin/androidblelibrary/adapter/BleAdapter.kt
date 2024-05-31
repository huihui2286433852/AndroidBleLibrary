package com.scin.androidblelibrary.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.scin.androidblelibrary.R
import com.scin.androidblelibrary.bean.BleDevicesBean

class BleAdapter:BaseQuickAdapter<BleDevicesBean, BaseViewHolder>(R.layout.item_ble) {
    override fun convert(holder: BaseViewHolder, item: BleDevicesBean) {
        holder.setText(R.id.tvName,"${item.name}+++${item.address}")
    }
}