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
import dev.useclique.android.api.TranscriptTurn

class TranscriptFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var sessionId: String
    private lateinit var list: RecyclerView
    private lateinit var message: TextView
    private lateinit var error: TextView
    private lateinit var progress: ProgressBar
    private val turns = mutableListOf<TranscriptTurn>()
    private var cliLabel = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
        sessionId = requireArguments().getString(ARG_SESSION)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_transcript, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.transcript)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        list = view.findViewById(R.id.list)
        message = view.findViewById(R.id.message)
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
                val transcript = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).transcript(sessionId)
                }
                if (!isAdded) return@launch
                progress.visibility = View.GONE
                cliLabel = transcript.cli
                turns.clear()
                turns.addAll(transcript.turns)
                if (turns.isEmpty()) {
                    message.visibility = View.VISIBLE
                } else {
                    list.visibility = View.VISIBLE
                    list.adapter?.notifyDataSetChanged()
                }
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

    private inner class Adapter : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            return RowHolder(layoutInflater.inflate(R.layout.item_transcript, parent, false))
        }

        override fun getItemCount() = turns.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            holder.bind(turns[position])
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val role: TextView = view.findViewById(R.id.role)
        private val text: TextView = view.findViewById(R.id.text)

        fun bind(turn: TranscriptTurn) {
            role.text = if (turn.role == "user") {
                getString(R.string.transcript_you)
            } else {
                cliLabel.ifBlank { getString(R.string.transcript_assistant) }
            }
            text.text = turn.text
        }
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        private const val ARG_SESSION = "sessionId"

        fun newInstance(serverId: String, sessionId: String) = TranscriptFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERVER, serverId)
                putString(ARG_SESSION, sessionId)
            }
        }
    }
}
