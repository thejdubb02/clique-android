package dev.useclique.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.notify.WaitService
import dev.useclique.android.term.TerminalBridge

/**
 * Terminal in a WebView, prompt in a native EditText.
 * The EditText is [R.id.prompt_input] in fragment_session.xml, an
 * android.widget.EditText, never a WebView input.
 */
class SessionFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var sessionId: String
    private var bridge: TerminalBridge? = null
    private var sessionName: String = ""
    private lateinit var prompt: EditText

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* watching still starts; the notification may be silent if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
        sessionId = requireArguments().getString(ARG_SESSION)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_session, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = activity as MainActivity
        val store = act.app.store
        val server = store.get(serverId) ?: return
        val token = store.token(serverId)
        sessionName = sessionId

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = sessionId
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.inflateMenu(R.menu.session)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.kill -> runOp { it.kill(sessionId) }
                R.id.start -> runOp { it.start(sessionId) }
                R.id.delete -> confirmDelete(sessionName) {
                    runOp(after = { parentFragmentManager.popBackStack() }) { it.delete(sessionId) }
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }


        prompt = view.findViewById(R.id.prompt_input)
        // Real EditText. Enter sends; Shift+Enter inserts a newline.
        prompt.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendPrompt()
                true
            } else if (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed
            ) {
                sendPrompt()
                true
            } else {
                false
            }
        }
        prompt.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                if (event.isShiftPressed) {
                    false
                } else {
                    sendPrompt()
                    true
                }
            } else {
                false
            }
        }
        view.findViewById<View>(R.id.send).setOnClickListener { sendPrompt() }
        bindKeyBar(view.findViewById(R.id.key_bar))

        val web = view.findViewById<WebView>(R.id.terminal)
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        val client = CliqueClient.forServer(server, token)
        bridge = TerminalBridge(web, client, sessionId).also { it.attach() }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val state = withContext(Dispatchers.IO) { client.state() }
                val session = state.sessions.firstOrNull { it.id == sessionId }
                if (session != null && isAdded) {
                    sessionName = session.name
                    toolbar.title = session.name
                    toolbar.subtitle = session.cliLabel
                }
            } catch (_: Exception) {
            }
        }

        maybeAskNotifications()
    }

    override fun onResume() {
        super.onResume()
        WaitService.onScreen = "$serverId:$sessionId"
        bridge?.hold()
    }

    override fun onPause() {
        if (WaitService.onScreen == "$serverId:$sessionId") WaitService.onScreen = null
        bridge?.release()
        super.onPause()
    }

    override fun onDestroyView() {
        bridge?.detach()
        bridge = null
        super.onDestroyView()
    }

    private fun bindKeyBar(row: LinearLayout) {
        val minPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            resources.displayMetrics,
        ).toInt()
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            resources.displayMetrics,
        ).toInt()
        for (key in KEY_BAR_KEYS) {
            val btn = Button(requireContext()).apply {
                text = getString(key.labelRes)
                contentDescription = getString(key.spokenRes)
                isAllCaps = false
                isFocusable = false
                isFocusableInTouchMode = false
                setTextColor(ContextCompat.getColor(context, R.color.text))
                background = ContextCompat.getDrawable(context, R.drawable.field_bg)
                minHeight = minPx
                minimumHeight = minPx
                minWidth = minPx
                minimumWidth = minPx
                setPadding(pad * 3, pad, pad * 3, pad)
                gravity = Gravity.CENTER
                setOnClickListener { sendKeystroke(key.tmuxName) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = pad
                marginEnd = pad
            }
            row.addView(btn, lp)
        }
    }

    private fun sendKeystroke(key: String) {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).sendKey(sessionId, key)
                }
                // Enter on a permission prompt starts a turn, so the same
                // finish notification a typed prompt gets applies here. The
                // keys that stop work instead make this return immediately.
                WaitService.watch(requireContext(), serverId, sessionId, sessionName)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: getString(R.string.send_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendPrompt() {
        val text = prompt.text.toString()
        if (text.isEmpty()) return
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        prompt.text.clear()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).send(sessionId, text, enter = true)
                }
                WaitService.watch(requireContext(), serverId, sessionId, sessionName)
            } catch (e: Exception) {
                // The field was cleared optimistically. Put it back rather than
                // making him retype it from a toast.
                if (prompt.text.isEmpty()) {
                    prompt.setText(text)
                    prompt.setSelection(text.length)
                }
                Toast.makeText(requireContext(), e.message ?: getString(R.string.send_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runOp(after: () -> Unit = {}, op: (CliqueClient) -> Unit) {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { op(CliqueClient.forServer(server, token)) }
                after()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        private const val ARG_SESSION = "sessionId"

        fun newInstance(serverId: String, sessionId: String) = SessionFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERVER, serverId)
                putString(ARG_SESSION, sessionId)
            }
        }
    }
}
