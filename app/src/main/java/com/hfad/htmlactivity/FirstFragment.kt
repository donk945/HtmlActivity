package com.hfad.htmlactivity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hfad.htmlactivity.databinding.FragmentFirstBinding
import kotlinx.coroutines.launch

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()

    // VLM 分支：当前预览中的图片
    private var previewBitmap: Bitmap? = null

    // 拍照
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { onImagePicked(it) }
    }

    // 相册选图
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = requireContext().contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.let { b -> onImagePicked(b) }
        }
    }

    private fun onImagePicked(bitmap: Bitmap) {
        previewBitmap = bitmap
        binding.ivImagePreview.setImageBitmap(bitmap)
        binding.ivImagePreview.visibility = View.VISIBLE
        viewModel.attachImage(bitmap)
    }

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

        // 清空对话按钮
        binding.btnClear.setOnClickListener {
            viewModel.clearConversation()
        }

        // 拍照按钮
        binding.btnCamera.setOnClickListener {
            takePictureLauncher.launch(null)
        }

        // 相册选图按钮
        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 点击预览图取消选中
        binding.ivImagePreview.setOnClickListener {
            previewBitmap = null
            binding.ivImagePreview.setImageBitmap(null)
            binding.ivImagePreview.visibility = View.GONE
            viewModel.clearPendingImage()
        }

        // 点击发送按钮 → 委托给 ViewModel
        binding.btnSend.setOnClickListener {
            val userInput = binding.etInput.text.toString().trim()
            val hasImage = previewBitmap != null
            if (userInput.isNotEmpty() || hasImage) {
                binding.etInput.text.clear()
                previewBitmap = null
                binding.ivImagePreview.setImageBitmap(null)
                binding.ivImagePreview.visibility = View.GONE
                viewModel.sendMessage(userInput.ifBlank { "请描述这张图片" })
            }
        }

        // 观察 ViewModel 的 UI 状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {//repeatOnLifecycle(STARTED) — 在 Fragment 可见时收集 StateFlow，不可见时自动取消，避免后台泄漏
                viewModel.uiState.collect { state ->
                    // 防止 Fragment 销毁后异步回调访问 null binding
                    val binding = _binding ?: return@collect

                    // 提交消息列表，DiffUtil 自动处理增量更新
                    chatAdapter.submitList(state.messages) {
                        // 列表提交后滚动到底部
                        if (chatAdapter.itemCount > 0) {
                            binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                    // 加载中禁用发送按钮
                    binding.btnSend.isEnabled = !state.isLoading

                    // 检测到 HTML_GENERATE 意图 → 导航到 WebView
                    if (state.intentType == IntentType.HTML_GENERATE && state.htmlContent != null) {
                        val htmlContent = state.htmlContent!!
                        viewModel.onHtmlConsumed() // 先清除 intentType 避免重复导航
                        val bundle = Bundle().apply {
                            putString("htmlContent", htmlContent)
                        }
                        findNavController().navigate(
                            R.id.action_firstFragment_to_htmlDisplayFragment,
                            bundle
                        )
                    }

                    // 图片已被 ViewModel 消费 → 同步隐藏预览
                    if (!state.hasPendingImage && previewBitmap != null) {
                        previewBitmap = null
                        binding.ivImagePreview.setImageBitmap(null)
                        binding.ivImagePreview.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
