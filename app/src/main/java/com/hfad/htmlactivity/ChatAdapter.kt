package com.hfad.htmlactivity

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import com.hfad.htmlactivity.databinding.ItemMessageBinding

// 类定义：继承 RecyclerView.Adapter，泛型指定为自定义的 ViewHolder
class ChatAdapter(
    // 构造函数参数：接收消息列表，MutableList 表示可以增删
    private val messages: MutableList<Message>
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    // 内部类 ViewHolder：持有 item 视图的引用
    // inner 关键字让 ViewHolder 可以访问外部 Adapter 的属性和方法
    inner class ViewHolder(
        val binding: ItemMessageBinding  // ViewBinding 对象，包含 item 的所有控件
    ) : RecyclerView.ViewHolder(binding.root) {  // 父类构造函数，传入 item 的根布局

        // 自定义方法：把数据绑定到视图上
        fun bind(message: Message) {
            // 设置消息文本
            binding.tvMessage.text = message.content

            // 根据消息类型设置不同的样式
            if (message.isUser) {
                // 用户消息：蓝色气泡
                binding.bubbleLayout.setBackgroundResource(R.drawable.bubble_user)
                // 动态修改布局参数：靠右对齐
                binding.bubbleLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = android.view.Gravity.END
                }
            } else {
                // AI 消息：灰色气泡
                binding.bubbleLayout.setBackgroundResource(R.drawable.bubble_ai)
                // 动态修改布局参数：靠左对齐
                binding.bubbleLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = android.view.Gravity.START
                }
            }
        }
    }

    // 必须重写方法：创建 ViewHolder
    // 只在需要新 ViewHolder 时调用，比如首次显示或创建缓冲
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 用 ViewBinding 填充布局
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),  // 获取布局填充器
            parent,                                // 父容器，也就是 RecyclerView
            false                                  // false 表示不立即添加到父容器
        )
        return ViewHolder(binding)  // 返回持有 binding 的 ViewHolder
    }

    // 必须重写方法：绑定数据到 ViewHolder
    // 滚动时反复调用，把新数据填充到复用的 ViewHolder 上
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])  // 调用 ViewHolder 的 bind 方法
    }

    // 必须重写方法：返回数据总数
    override fun getItemCount() = messages.size  // Kotlin 简写，等于 return messages.size

    // 自定义方法：添加新消息
    fun addMessage(message: Message) {
        messages.add(message)                       // 把消息加入数据列表
        notifyItemInserted(messages.size - 1)       // 通知 RecyclerView 插入了一条新数据
        // notifyItemInserted 的好处：局部刷新，还有插入动画
    }
}