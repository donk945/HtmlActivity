package com.hfad.htmlactivity.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.hfad.htmlactivity.R
import com.hfad.htmlactivity.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSave.setOnClickListener {
            viewModel.save(
                binding.etBaseUrl.text.toString(),
                binding.etModel.text.toString(),
                binding.etApiKey.text.toString()
            )
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect

                    // 首次加载后回填输入框
                    if (!state.isLoading && b.etBaseUrl.text.isNullOrEmpty()) {
                        b.etBaseUrl.setText(state.baseUrl)
                        b.etModel.setText(state.model)
                        b.etApiKey.setText(state.apiKey)
                    }

                    b.btnSave.isEnabled = !state.isSaving
                    b.btnLogout.isEnabled = !state.isSaving

                    if (state.error != null) {
                        b.tvError.text = state.error
                        b.tvError.visibility = View.VISIBLE
                    }

                    if (state.saved) {
                        viewModel.consumeSaved()
                        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                    }

                    if (state.loggedOut) {
                        findNavController().navigate(
                            R.id.loginFragment,
                            null,
                            NavOptions.Builder().setPopUpTo(0, true).build()
                        )
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
