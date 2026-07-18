package com.hfad.htmlactivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hfad.htmlactivity.databinding.FragmentFirstBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    //历史记录
    private val historyList = mutableListOf<Pair<String, String>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
        chatAdapter = ChatAdapter(messages)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
        // 点击发送按钮
        binding.btnSend.setOnClickListener {
            android.util.Log.d("FirstFragment", "按钮被点击了")
            val userInput = binding.etInput.text.toString().trim()
            android.util.Log.d("FirstFragment", "输入内容: $userInput")
            if (userInput.isNotEmpty()) {
                // 添加用户消息
                val userMessage = Message(userInput, true)
                chatAdapter.addMessage(userMessage)
                binding.etInput.text.clear()

                // 滚动到最后一条消息
                binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)

                // 发送给AI
                sendToDeepSeek(userInput)
            }
        }
    }

    private fun sendToDeepSeek(userPrompt: String) {
        binding.btnSend.isEnabled = false

        // 添加一个临时的"正在输入..."消息
        val loadingMessage = Message("正在思考...", false)
        chatAdapter.addMessage(loadingMessage)
        val loadingIndex = chatAdapter.itemCount - 1

        val requestJson = JSONObject().apply {
            put("model", "deepseek-chat")
            put("stream", false)
            put("temperature", 0.7)
            put("messages", JSONArray().apply {

                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是一个有帮助的AI助手，请用友好的方式回答用户的问题。")
                })

                for ((role, content) in historyList) {
                    put(JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }

                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer sk-356fa9ca91ad4a4abf7460179e2a5b69")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    // 替换加载消息为错误消息
                    messages[loadingIndex] = Message("网络错误: ${e.message}", false)
                    chatAdapter.notifyItemChanged(loadingIndex)
                    binding.btnSend.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val aiResponse = try {
                    val body = response.body?.string() ?: ""
                    JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } catch (e: Exception) {
                    "解析失败: ${e.message}"
                }

                activity?.runOnUiThread {
                    // 替换加载消息为AI回复
                    messages[loadingIndex] = Message(aiResponse, false)
                    chatAdapter.notifyItemChanged(loadingIndex)
                    binding.btnSend.isEnabled = true

                    // 滚动到最新消息
                    binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    //添加历史记录
                    historyList.add("user" to userPrompt)
                    historyList.add("assistant" to aiResponse)
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}