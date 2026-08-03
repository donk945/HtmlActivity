package com.hfad.htmlactivity

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hfad.htmlactivity.databinding.ItemMessageBinding

/**
 * 聊天消息的 RecyclerView 适配器
 * 使用 ListAdapter + DiffUtil 实现高效增量更新和动画
 */
class ChatAdapter : ListAdapter<Message, ChatAdapter.ViewHolder>(DiffCallback) {

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem == newItem
        }
    }

    inner class ViewHolder(
        val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvMessage.text = message.content

            if (message.isUser) {
                // 用户消息：蓝色气泡，靠右
                binding.bubbleLayout.setBackgroundResource(R.drawable.bubble_user)
                binding.bubbleLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = android.view.Gravity.END
                }
            } else {
                // AI 消息：灰色气泡，靠左
                binding.bubbleLayout.setBackgroundResource(R.drawable.bubble_ai)
                binding.bubbleLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = android.view.Gravity.START
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
