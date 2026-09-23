package dev.useclique.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.ResumableConversation

class ResumableFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var error: TextView
    private lateinit var progress: ProgressBar
    private val rows = mutableListOf<ResumableConversation>()
    private var creating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_resumable, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.resume_title)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        list = view.findViewById(R.id.list)
        empty = view.findViewById(R.id.empty)
        error = view.findViewById(R.id.error)
        progress = view.findViewById(R.id.progress)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = Adapter()
        load()
    }

    private fun load() {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).resumable()
                }
                if (!isAdded) return@launch
                rows.clear()
                rows.addAll(found.sortedByDescending { it.updated })
                list.adapter?.notifyDataSetChanged()
                progress.visibility = View.GONE
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isAdded) return@launch
                progress.visibility = View.GONE
                error.text = e.message ?: getString(R.string.could_not_load)
                error.visibility = View.VISIBLE
            }
        }
    }

    private fun resume(row: ResumableConversation) {
        if (creating) return
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        creating = true
        error.text = ""
        error.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).createSession(
                        cli = row.cli,
                        cwd = row.cwd,
                        name = row.project,
                        folder = row.folder.ifEmpty { null },
                        cliSessionId = row.cliSessionId,
                    )
                }
                if (!isAdded) return@launch
                parentFragmentManager.popBackStack()
                act.showSession(serverId, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                creating = false
                if (!isAdded) return@launch
                error.text = e.message ?: getString(R.string.create_failed)
                error.visibility = View.VISIBLE
            }
        }
    }

    private fun relativeTime(updatedSeconds: Double): String {
        val now = System.currentTimeMillis() / 1000.0
        val delta = (now - updatedSeconds).toLong().coerceAtLeast(0L)
        return when {
            delta < 3600L -> getString(R.string.ago_minutes, delta / 60L)
            delta < 86400L -> getString(R.string.ago_hours, delta / 3600L)
            else -> getString(R.string.ago_days, delta / 86400L)
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            return RowHolder(layoutInflater.inflate(R.layout.item_resumable, parent, false))
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            holder.bind(rows[position])
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.label)
        private val meta: TextView = view.findViewById(R.id.meta)

        fun bind(row: ResumableConversation) {
            label.text = row.label
            val parts = ArrayList<String>(4)
            if (row.project.isNotBlank()) parts.add(row.project)
            if (row.cli.isNotBlank()) parts.add(row.cli)
            parts.add(relativeTime(row.updated))
            if (row.branch.isNotBlank()) parts.add(row.branch)
            meta.text = parts.joinToString(" · ")
            itemView.setOnClickListener { resume(row) }
        }
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        fun newInstance(serverId: String) = ResumableFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERVER, serverId) }
        }
    }
}
