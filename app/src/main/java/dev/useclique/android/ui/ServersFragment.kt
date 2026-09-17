package dev.useclique.android.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.store.Server

class ServersFragment : Fragment() {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val rows = mutableListOf<Row>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_servers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.servers_title)
        list = view.findViewById(R.id.list)
        empty = view.findViewById(R.id.empty)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = Adapter()
        view.findViewById<View>(R.id.add).setOnClickListener {
            (activity as MainActivity).showEditServer(null)
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val store = (activity as MainActivity).app.store
        val servers = store.servers
        rows.clear()
        rows.addAll(servers.map { Row(it, store.token(it.id) != null, Reach.Checking) })
        list.adapter?.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        rows.forEachIndexed { index, row -> probe(index, row.server) }
    }

    private fun probe(index: Int, server: Server) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    CliqueClient.forServer(server, null).healthz()
                } catch (_: Exception) {
                    false
                }
            }
            if (!isAdded || index >= rows.size || rows[index].server.id != server.id) return@launch
            rows[index] = rows[index].copy(reach = if (ok) Reach.Up else Reach.Down)
            list.adapter?.notifyItemChanged(index)
        }
    }

    private data class Row(val server: Server, val paired: Boolean, val reach: Reach)

    private enum class Reach { Checking, Up, Down }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = layoutInflater.inflate(R.layout.item_server, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.name.text = row.server.name.ifBlank { row.server.baseUrl }
            holder.url.text = row.server.baseUrl
            holder.paired.text = getString(if (row.paired) R.string.paired else R.string.not_paired)
            holder.status.setText(
                when (row.reach) {
                    Reach.Checking -> R.string.checking
                    Reach.Up -> R.string.reachable
                    Reach.Down -> R.string.unreachable
                },
            )
            val color = when (row.reach) {
                Reach.Up -> R.color.alive
                Reach.Down -> R.color.error
                Reach.Checking -> R.color.stopped
            }
            val c = ContextCompat.getColor(holder.itemView.context, color)
            val bg = holder.reach.background?.mutate()
            if (bg is GradientDrawable) bg.setColor(c) else holder.reach.setBackgroundColor(c)
            holder.itemView.setOnClickListener {
                val act = activity as MainActivity
                if (row.paired) act.showSessions(row.server.id) else act.showEditServer(row.server.id)
            }
            holder.itemView.setOnLongClickListener {
                (activity as MainActivity).showEditServer(row.server.id)
                true
            }
        }
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val url: TextView = view.findViewById(R.id.url)
        val paired: TextView = view.findViewById(R.id.paired)
        val status: TextView = view.findViewById(R.id.status)
        val reach: View = view.findViewById(R.id.reach)
    }
}
