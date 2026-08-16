package com.hfad.htmlactivity.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hfad.htmlactivity.databinding.FragmentConversationListBinding
import kotlinx.coroutines.launch

class ConversationListFragment : Fragment() {

    private var _binding: FragmentConversationListBinding? = null
    private val binding get() = _binding!!

    // 记录上一次已展示的错误，避免同一个错误反复弹 Toast
    private var lastShownError: String? = null

    private val viewModel: ChatViewModel by activityViewModels()

    private val adapter = ConversationAdapter(
        onClick = { c ->
            viewModel.selectConversation(c.id)
            findNavController().navigateUp()
        },
        onDelete = { c -> viewModel.deleteConversation(c.id) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ConversationListFragment.adapter
        }

        binding.btnNew.setOnClickListener {
            viewModel.createNewConversation()
            findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect
                    adapter.submitList(state.conversations)
                    b.tvEmpty.visibility =
                        if (state.conversations.isEmpty()) View.VISIBLE else View.GONE

                    state.error?.let { msg ->
                        if (msg != lastShownError) {
                            lastShownError = msg
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
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
