package com.hfad.htmlactivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hfad.htmlactivity.databinding.FragmentFirstBinding
import kotlinx.coroutines.launch

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置 RecyclerView
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }

        // 点击发送按钮 → 委托给 ViewModel
        binding.btnSend.setOnClickListener {
            val userInput = binding.etInput.text.toString().trim()
            if (userInput.isNotEmpty()) {
                binding.etInput.text.clear() //清空输入框
                viewModel.sendMessage(userInput)  //委托给viewmodel
            }
        }

        // 观察 ViewModel 的 UI 状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 提交消息列表，DiffUtil 自动处理增量更新
                    chatAdapter.submitList(state.messages) {
                        // 列表提交后滚动到底部
                        if (chatAdapter.itemCount > 0) {
                            binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                    // 加载中禁用发送按钮
                    binding.btnSend.isEnabled = !state.isLoading
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
