package dev.useclique.android.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.GROUP_ARCHIVED
import dev.useclique.android.api.GROUP_RUNNING
import dev.useclique.android.api.GROUP_UNGROUPED
import dev.useclique.android.api.PanelState
import dev.useclique.android.api.Session
import dev.useclique.android.api.SessionListItem
import dev.useclique.android.api.groupSessions
import dev.useclique.android.api.parseFolderColor
import dev.useclique.android.api.wantsPermission

class SessionsFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private val items = mutableListOf<Item>()
    private var lastState: PanelState? = null
    private var query: String = ""
    private var activeOnly: Boolean = true
    /*
      Same quiet window as SessionFragment. The poll is every three seconds,
      so an answered prompt can still read as waiting on the next pass and
      put the buttons back under a thumb that is still there.
    */
    private val answeredUntil = HashMap<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (!isResumed) return
            load(silent = true)
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_sessions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId)
        activeOnly = requireContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE_ONLY, true)
        toolbar = view.findViewById(R.id.toolbar)
        toolbar.title = server?.name ?: getString(R.string.sessions_title)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { act.showServers() }
        toolbar.inflateMenu(R.menu.sessions)
        setupSearch(toolbar.menu.findItem(R.id.search))
        syncToggle()
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.edit_server -> {
                    act.showEditServer(serverId)
                    true
                }
                R.id.active_only -> {
                    setActiveOnly(!activeOnly)
                    true
                }
                else -> false
            }
        }
        list = view.findViewById(R.id.list)
        empty = view.findViewById(R.id.empty)
        refresh = view.findViewById(R.id.refresh)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = Adapter()
        refresh.setOnRefreshListener { load(silent = false) }
        view.findViewById<View>(R.id.new_session).setOnClickListener { act.showNewSession(serverId) }
        load(silent = false)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 3000)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun load(silent: Boolean) {
        val act = activity as? MainActivity ?: return
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        if (!silent) refresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).state()
                }
                bind(state)
            } catch (e: Exception) {
                if (!silent) {
                    Toast.makeText(requireContext(), e.message ?: getString(R.string.load_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                refresh.isRefreshing = false
            }
        }
    }

    private fun setupSearch(item: MenuItem?) {
        val searchView = item?.actionView as? SearchView ?: return
        searchView.queryHint = getString(R.string.search_sessions)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.hint))
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean {
                setQuery(q)
                searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(q: String): Boolean {
                setQuery(q)
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                setQuery("")
                return true
            }
        })
    }

    private fun setQuery(q: String) {
        if (query == q) return
        query = q
        lastState?.let { bind(it) }
    }

    private fun setActiveOnly(on: Boolean) {
        if (activeOnly == on) return
        activeOnly = on
        requireContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE_ONLY, on)
            .apply()
        syncToggle()
        lastState?.let { bind(it) }
    }

    private fun syncToggle() {
        if (!::toolbar.isInitialized) return
        val item = toolbar.menu.findItem(R.id.active_only) ?: return
        item.isChecked = activeOnly
        item.setTitle(if (activeOnly) R.string.filter_running else R.string.filter_all)
        item.contentDescription = getString(
            if (activeOnly) R.string.showing_running else R.string.showing_all,
        )
    }

    private fun bind(state: PanelState) {
        lastState = state
        items.clear()
        val folderNames = state.folders.associate { it.id to it.name }
        val folderColors = state.folders.associate { it.id to it.color }
        for (item in groupSessions(state.sessions, state.folders, query, activeOnly)) {
            when (item) {
                is SessionListItem.Header -> items.add(
                    Item.Header(headerTitle(item.id, folderNames), folderColors[item.id]),
                )
                is SessionListItem.Row -> items.add(Item.Row(item.session))
            }
        }
        list.adapter?.notifyDataSetChanged()
        when {
            state.sessions.isEmpty() -> {
                empty.text = getString(R.string.empty_sessions)
                empty.visibility = View.VISIBLE
            }
            items.isEmpty() -> {
                empty.text = getString(R.string.empty_filter)
                empty.visibility = View.VISIBLE
            }
            else -> empty.visibility = View.GONE
        }
    }

    private fun headerTitle(id: String, folderNames: Map<String, String>): String {
        return when (id) {
            GROUP_RUNNING -> getString(R.string.running)
            GROUP_UNGROUPED -> getString(R.string.ungrouped)
            GROUP_ARCHIVED -> getString(R.string.archived)
            else -> folderNames[id] ?: id
        }
    }

    private sealed class Item {
        data class Header(val title: String, val color: String? = null) : Item()
        data class Row(val session: Session) : Item()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) = when (items[position]) {
            is Item.Header -> 0
            is Item.Row -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                HeaderHolder(layoutInflater.inflate(R.layout.item_folder, parent, false))
            } else {
                RowHolder(layoutInflater.inflate(R.layout.item_session, parent, false))
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> (holder as HeaderHolder).bind(item.title, item.color)
                is Item.Row -> (holder as RowHolder).bind(item.session)
            }
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.title)
        private val colorDot: View = view.findViewById(R.id.folder_color)

        fun bind(titleText: String, rawColor: String?) {
            title.text = titleText
            val fallback = ContextCompat.getColor(itemView.context, R.color.muted)
            val color = parseFolderColor(rawColor, fallback)
            if (rawColor.isNullOrBlank()) {
                colorDot.visibility = View.GONE
            } else {
                colorDot.visibility = View.VISIBLE
                val bg = colorDot.background?.mutate()
                if (bg is GradientDrawable) bg.setColor(color) else colorDot.setBackgroundColor(color)
            }
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val cli: TextView = view.findViewById(R.id.cli)
        val meta: TextView = view.findViewById(R.id.meta)
        val dot: View = view.findViewById(R.id.dot)
        val actions: View = view.findViewById(R.id.permission_actions)
        val approve: View = view.findViewById(R.id.permission_approve)
        val deny: View = view.findViewById(R.id.permission_deny)

        fun bind(session: Session) {
            name.text = session.name
            cli.text = session.cliLabel
            val status = session.state.ifBlank { if (session.alive) "idle" else "stopped" }
            val extra = session.saying.ifBlank { session.cwd }
            meta.text = "$status · $extra"
            val color = when (status) {
                "working" -> R.color.violet
                "waiting" -> R.color.waiting
                "error" -> R.color.error
                "stopped" -> R.color.stopped
                else -> if (session.alive) R.color.alive else R.color.stopped
            }
            val c = ContextCompat.getColor(itemView.context, color)
            val bg = dot.background?.mutate()
            if (bg is GradientDrawable) bg.setColor(c) else dot.setBackgroundColor(c)
            val quiet = System.currentTimeMillis() < (answeredUntil[session.id] ?: 0L)
            actions.visibility = if (!quiet && wantsPermission(session)) View.VISIBLE else View.GONE
            approve.setOnClickListener { answerFromList(session, "Enter", actions) }
            deny.setOnClickListener { answerFromList(session, "Escape", actions) }
            itemView.setOnClickListener {
                (activity as MainActivity).showSession(serverId, session.id)
            }
            itemView.setOnLongClickListener {
                showActions(session)
                true
            }
        }
    }

    private fun answerFromList(session: Session, key: String, actions: View) {
        if (actions.visibility != View.VISIBLE) return
        answeredUntil[session.id] = System.currentTimeMillis() + ANSWER_QUIET_MS
        actions.visibility = View.GONE
        runOp { it.sendKey(session.id, key) }
    }

    private fun showActions(session: Session) {
        val options = mutableListOf<String>()
        if (session.alive) options.add(getString(R.string.interrupt))
        if (session.alive) options.add(getString(R.string.kill)) else options.add(getString(R.string.start))
        options.add(getString(R.string.rename))
        options.add(getString(R.string.move_to_folder))
        options.add(getString(R.string.delete))
        AlertDialog.Builder(requireContext())
            .setTitle(session.name)
            .setItems(options.toTypedArray()) { _, which ->
                val label = options[which]
                when (label) {
                    getString(R.string.interrupt) -> runOp { it.sendKey(session.id, "C-c") }
                    getString(R.string.kill) -> runOp { it.kill(session.id) }
                    getString(R.string.start) -> runOp { it.start(session.id) }
                    getString(R.string.rename) -> showRename(session)
                    getString(R.string.move_to_folder) -> showMove(session)
                    getString(R.string.delete) -> confirmDelete(session.name) {
                        runOp { it.delete(session.id) }
                    }
                }
            }
            .show()
    }

    private fun showRename(session: Session) {
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            resources.displayMetrics,
        ).toInt()
        val input = EditText(requireContext()).apply {
            setText(session.name)
            setSelection(session.name.length)
            hint = getString(R.string.session_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setHintTextColor(ContextCompat.getColor(context, R.color.hint))
            background = ContextCompat.getDrawable(context, R.drawable.field_bg)
            setPadding(pad, pad / 2, pad, pad / 2)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val box = FrameLayout(requireContext()).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.rename) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.rename_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runOp { it.updateSession(session.id, name = newName) }
            }
            .show()
    }

    private fun showMove(session: Session) {
        val folders = lastState?.folders ?: emptyList()
        val labels = mutableListOf(getString(R.string.ungrouped))
        labels.addAll(folders.map { it.name })
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.move_to_folder)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    runOp { it.updateSession(session.id, clearFolder = true) }
                } else {
                    runOp { it.updateSession(session.id, folder = folders[which - 1].id) }
                }
            }
            .show()
    }

    private fun runOp(op: (CliqueClient) -> Unit) {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { op(CliqueClient.forServer(server, token)) }
                load(silent = true)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        private const val PREFS = "session_list"
        private const val KEY_ACTIVE_ONLY = "active_only"
        private const val ANSWER_QUIET_MS = 6000L
        fun newInstance(serverId: String) = SessionsFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERVER, serverId) }
        }
    }
}
