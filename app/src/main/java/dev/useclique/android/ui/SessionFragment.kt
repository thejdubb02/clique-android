package dev.useclique.android.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.Prompt
import dev.useclique.android.api.Session
import dev.useclique.android.api.openableUrl
import dev.useclique.android.api.wantsPermission
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
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private val handler = Handler(Looper.getMainLooper())

    /*
      Hiding the banner on tap is not enough on its own. The signal clears when
      the pane prints something after it, and the poll runs every three seconds,
      so an answered prompt can still read as waiting on the very next poll and
      put the buttons straight back under a thumb that is still there. Ignore
      the signal briefly after answering; if it is genuinely still asking, the
      poll after that says so.
    */
    private var answeredUntil = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (!isResumed) return
            refreshState()
            handler.postDelayed(this, 3000)
        }
    }

    private lateinit var attach: View

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* watching still starts; the notification may be silent if denied */ }

    /* The photo picker, which needs no permission at all: that is the whole
       reason it exists, and it is why this app still asks for nothing to read
       an image. */
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) attachImage(uri) }

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

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.title = ""
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.inflateMenu(R.menu.session)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.history -> showPromptHistory()
                R.id.selectText -> {
                    bridge?.readText(SELECT_TEXT_LINES) { text ->
                        if (!isAdded) return@readText
                        showSelectText(text)
                    }
                }
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
        attach = view.findViewById(R.id.attach)
        attach.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        view.findViewById<View>(R.id.permission_approve).setOnClickListener {
            answered(view)
            sendKeystroke("Enter")
        }
        view.findViewById<View>(R.id.permission_deny).setOnClickListener {
            answered(view)
            sendKeystroke("Escape")
        }
        bindKeyBar(view.findViewById(R.id.key_bar))

        val web = view.findViewById<WebView>(R.id.terminal)
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        val client = CliqueClient.forServer(server, token)
        bridge = TerminalBridge(web, client, sessionId).also {
            it.onOpenUrl = { url -> if (isAdded) openUrl(url) }
            it.attach()
        }

        refreshState()
        maybeAskNotifications()
    }

    override fun onResume() {
        super.onResume()
        WaitService.onScreen = "$serverId:$sessionId"
        bridge?.hold()
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 3000)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        if (WaitService.onScreen == "$serverId:$sessionId") WaitService.onScreen = null
        bridge?.release()
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(poll)
        bridge?.detach()
        bridge = null
        super.onDestroyView()
    }

    private fun refreshState() {
        val act = activity as? MainActivity ?: return
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).state()
                }
                val session = state.sessions.firstOrNull { it.id == sessionId } ?: return@launch
                if (isAdded) applySession(session)
            } catch (_: Exception) {
            }
        }
    }

    private fun answered(view: View) {
        answeredUntil = System.currentTimeMillis() + ANSWER_QUIET_MS
        view.findViewById<View>(R.id.permission_banner).visibility = View.GONE
    }

    private fun applySession(session: Session) {
        sessionName = session.name
        toolbar.title = session.name
        toolbar.subtitle = session.cliLabel
        val banner = view?.findViewById<View>(R.id.permission_banner) ?: return
        val text = view?.findViewById<TextView>(R.id.permission_text) ?: return
        if (System.currentTimeMillis() < answeredUntil) {
            banner.visibility = View.GONE
        } else if (wantsPermission(session)) {
            text.text = if (session.saying.isNotEmpty()) {
                getString(R.string.wants_approval) + " · " + session.saying
            } else {
                getString(R.string.wants_approval)
            }
            banner.visibility = View.VISIBLE
        } else {
            banner.visibility = View.GONE
        }
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

    private fun openUrl(url: String) {
        val safe = openableUrl(url) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.url_cannot_open, Toast.LENGTH_SHORT).show()
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

    /* An image goes into the session's own folder and its path goes into the
       prompt. It is never sent: the person says what they want asking about it
       first, which is the same rule the prompt history picker follows. */
    private fun attachImage(uri: Uri) {
        val act = activity as? MainActivity ?: return
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        attach.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val resolver = requireContext().contentResolver
                    val data = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("could not read the image")
                    data to imageName(resolver, uri)
                }
                // Refused here rather than by the server, which would answer
                // 413 and leave the person looking at nothing.
                if (bytes.size > MAX_IMAGE_BYTES) {
                    Toast.makeText(requireContext(), R.string.attach_too_big, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val path = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).paste(sessionId, bytes, name)
                }
                if (path.isBlank()) throw IOException("the panel saved nothing")
                insertIntoPrompt(path)
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        e.message ?: getString(R.string.attach_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                if (isAdded) attach.isEnabled = true
            }
        }
    }

    private fun imageName(resolver: android.content.ContentResolver, uri: Uri): String {
        val shown = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        // The server sniffs the real type from the bytes, so this is a label
        // rather than a claim about what the file is.
        return shown?.takeIf { it.isNotBlank() } ?: "shot.png"
    }

    private fun insertIntoPrompt(path: String) {
        val at = prompt.selectionEnd.takeIf { it >= 0 } ?: prompt.text.length
        prompt.text.insert(at, "$path ")
        prompt.requestFocus()
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

    // Drops the recalled text into the prompt. Does not send it.
    private fun showPromptHistory() {
        val act = activity as? MainActivity ?: return
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            resources.displayMetrics,
        ).toInt()
        val root = layoutInflater.inflate(R.layout.dialog_prompt_history, null)
        val search = root.findViewById<EditText>(R.id.search)
        val list = root.findViewById<RecyclerView>(R.id.list)
        val status = root.findViewById<TextView>(R.id.status)
        val listArea = root.findViewById<View>(R.id.list_area)
        val dm = resources.displayMetrics
        // Sized so Cancel survives the keyboard. Searching is the first thing
        // anyone does here, the IME then takes about 40% of a phone screen, and
        // a list measured against the whole screen pushed the buttons off the
        // bottom where only the back gesture could reach them.
        val chrome = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 220f, dm).toInt()
        val listHeight = minOf(
            (dm.heightPixels * 0.38f).toInt(),
            dm.heightPixels - chrome,
        ).coerceAtLeast(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160f, dm).toInt())
        (listArea.layoutParams as LinearLayout.LayoutParams).apply {
            height = listHeight
            weight = 0f
        }
        val box = FrameLayout(requireContext()).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(
                root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        var all = emptyList<Prompt>()
        var ready = false
        var failed = false
        val shown = mutableListOf<Prompt>()

        fun render() {
            if (failed) {
                status.visibility = View.VISIBLE
                status.setText(R.string.prompt_history_failed)
                shown.clear()
                list.adapter?.notifyDataSetChanged()
                return
            }
            if (!ready) {
                status.visibility = View.VISIBLE
                status.setText(R.string.prompt_history_loading)
                return
            }
            val q = search.text.toString()
            val filtered = if (q.isBlank()) all else all.filter { it.text.contains(q, ignoreCase = true) }
            shown.clear()
            shown.addAll(filtered)
            list.adapter?.notifyDataSetChanged()
            if (shown.isEmpty()) {
                status.visibility = View.VISIBLE
                status.setText(R.string.prompt_history_empty)
            } else {
                status.visibility = View.GONE
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.prompt_history_title)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .create()

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = object : RecyclerView.Adapter<PromptHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptHolder {
                val v = layoutInflater.inflate(R.layout.item_prompt, parent, false)
                return PromptHolder(v)
            }

            override fun getItemCount() = shown.size

            override fun onBindViewHolder(holder: PromptHolder, position: Int) {
                val item = shown[position]
                holder.text.text = item.text
                val where = item.project.ifBlank { item.cwd }
                holder.meta.text = where
                holder.meta.visibility = if (where.isBlank()) View.GONE else View.VISIBLE
                holder.itemView.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val chosen = shown[pos]
                    prompt.setText(chosen.text)
                    prompt.setSelection(chosen.text.length)
                    dialog.dismiss()
                    prompt.requestFocus()
                }
            }
        }

        search.doAfterTextChanged { render() }
        dialog.show()
        val width = minOf(
            (dm.widthPixels * 0.9f).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 480f, dm).toInt(),
        )
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).prompts(400)
                }
                if (!isAdded || !dialog.isShowing) return@launch
                all = rows
                ready = true
                render()
            } catch (_: Exception) {
                if (!isAdded || !dialog.isShowing) return@launch
                failed = true
                render()
            }
        }
    }

    private fun showSelectText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(requireContext(), R.string.select_text_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            resources.displayMetrics,
        ).toInt()
        // The real count, not the cap. A pane that has printed twelve lines
        // should not be headed "Last 500 lines".
        val shown = text.split('\n').size
        val body = TextView(requireContext()).apply {
            setHorizontallyScrolling(true)
            this.text = text
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setPadding(pad, pad, pad, pad)
            /*
              A terminal row is already wrapped, to the pane's width. Wrapping
              it again at the dialog's narrower width breaks every row in two
              and the output stops being readable: "...model call and re" then
              "writes the story records". So the rows are kept whole and the
              view scrolls sideways instead.

              The width is set outright rather than left to
              setHorizontallyScrolling, which does not survive
              setTextIsSelectable on this path: tried both orders on a device
              and the text wrapped either way. Measuring the longest row is
              deterministic. Capped, because one runaway line should not make a
              view thousands of columns wide; anything past the cap wraps, as
              it did before.
            */
            val widest = text.lineSequence().maxOfOrNull { it.length } ?: 0
            width = (paint.measureText("M") * minOf(widest, 400)).toInt() + pad * 2
        }
        val wide = HorizontalScrollView(requireContext()).apply {
            addView(
                body,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val scroll = ScrollView(requireContext()).apply {
            addView(
                wide,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_text_title, shown))
            .setView(scroll)
            .setPositiveButton(R.string.copy_all) { _, _ ->
                val ctx = context ?: return@setPositiveButton
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
                Toast.makeText(
                    ctx,
                    getString(R.string.copied_lines, shown),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
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

    private class PromptHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.text)
        val meta: TextView = view.findViewById(R.id.meta)
    }

    companion object {
        private const val ANSWER_QUIET_MS = 6000L
        // 500 is a cap: the buffer holds 8000 and a selectable TextView
        // holding all of it is slow to select in.
        private const val SELECT_TEXT_LINES = 500
        // What the panel's /paste accepts. Checked here so a 12 MB photo is
        // refused with a sentence rather than a 413 nobody sees.
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
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
