package org.willhitestrategy.clique.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.willhitestrategy.clique.MainActivity
import org.willhitestrategy.clique.R
import org.willhitestrategy.clique.api.CliqueClient
import org.willhitestrategy.clique.api.PanelState
import org.willhitestrategy.clique.api.Session

class SessionsFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var refresh: SwipeRefreshLayout
    private val items = mutableListOf<Item>()
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
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = server?.name ?: getString(R.string.sessions_title)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { act.showServers() }
        toolbar.inflateMenu(R.menu.sessions)
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.edit_server) {
                act.showEditServer(serverId)
                true
            } else false
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

    private fun bind(state: PanelState) {
        items.clear()
        val byFolder = state.sessions.groupBy { it.folder }
        val folders = state.folders
        val seen = HashSet<String>()
        for (folder in folders) {
            val sessions = byFolder[folder.id].orEmpty()
            if (sessions.isEmpty()) continue
            items.add(Item.Header(folder.name))
            sessions.forEach { items.add(Item.Row(it)) }
            seen.add(folder.id)
        }
        val ungrouped = state.sessions.filter { it.folder == null || it.folder !in seen }
        if (ungrouped.isNotEmpty()) {
            items.add(Item.Header(getString(R.string.ungrouped)))
            ungrouped.forEach { items.add(Item.Row(it)) }
        }
        list.adapter?.notifyDataSetChanged()
        empty.visibility = if (state.sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private sealed class Item {
        data class Header(val title: String) : Item()
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
                is Item.Header -> (holder as HeaderHolder).title.text = item.title
                is Item.Row -> (holder as RowHolder).bind(item.session)
            }
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val cli: TextView = view.findViewById(R.id.cli)
        val meta: TextView = view.findViewById(R.id.meta)
        val dot: View = view.findViewById(R.id.dot)

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
            itemView.setOnClickListener {
                (activity as MainActivity).showSession(serverId, session.id)
            }
            itemView.setOnLongClickListener {
                showActions(session)
                true
            }
        }
    }

    private fun showActions(session: Session) {
        val act = activity as MainActivity
        val options = mutableListOf<String>()
        if (session.alive) options.add(getString(R.string.kill)) else options.add(getString(R.string.start))
        options.add(getString(R.string.delete))
        AlertDialog.Builder(requireContext())
            .setTitle(session.name)
            .setItems(options.toTypedArray()) { _, which ->
                val label = options[which]
                when (label) {
                    getString(R.string.kill) -> runOp { it.kill(session.id) }
                    getString(R.string.start) -> runOp { it.start(session.id) }
                    getString(R.string.delete) -> runOp { it.delete(session.id) }
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
        fun newInstance(serverId: String) = SessionsFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERVER, serverId) }
        }
    }
}
