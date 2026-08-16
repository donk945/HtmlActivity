package com.hfad.htmlactivity.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hfad.htmlactivity.IntentType
import com.hfad.htmlactivity.R
import com.hfad.htmlactivity.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by activityViewModels()
    private val chatAdapter = ChatAdapter()

    private var previewBitmap: Bitmap? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { onImagePicked(it) }
    }

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
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }

        binding.btnCamera.setOnClickListener {
            takePictureLauncher.launch(null)
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.ivImagePreview.setOnClickListener {
            previewBitmap = null
            binding.ivImagePreview.setImageBitmap(null)
            binding.ivImagePreview.visibility = View.GONE
            viewModel.clearPendingImage()
        }

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect

                    chatAdapter.submitList(state.messages) {
                        if (chatAdapter.itemCount > 0) {
                            b.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }

                    b.btnSend.isEnabled = !state.isLoading

                    b.tvError.visibility = if (state.error != null) View.VISIBLE else View.GONE
                    b.tvError.text = state.error.orEmpty()

                    if (state.intentType == IntentType.HTML_GENERATE && state.htmlContent != null) {
                        val htmlContent = state.htmlContent
                        viewModel.onHtmlConsumed()
                        val bundle = Bundle().apply { putString("htmlContent", htmlContent) }
                        findNavController().navigate(
                            R.id.action_chatFragment_to_htmlDisplayFragment,
                            bundle
                        )
                    }

                    if (!state.hasPendingImage && previewBitmap != null) {
                        previewBitmap = null
                        b.ivImagePreview.setImageBitmap(null)
                        b.ivImagePreview.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回时重新拉取模型配置，避免配置已保存但聊天页仍是旧值
        viewModel.refreshSettings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
