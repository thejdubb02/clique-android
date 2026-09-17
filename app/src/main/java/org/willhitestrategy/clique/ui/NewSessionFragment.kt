package org.willhitestrategy.clique.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.willhitestrategy.clique.MainActivity
import org.willhitestrategy.clique.R
import org.willhitestrategy.clique.api.CliqueClient
import org.willhitestrategy.clique.api.Folder
import org.willhitestrategy.clique.api.PanelState

class NewSessionFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var cli: Spinner
    private lateinit var cwd: EditText
    private lateinit var name: EditText
    private lateinit var folder: Spinner
    private lateinit var error: TextView
    private var state: PanelState? = null
    private val folderChoices = mutableListOf<Folder?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_new_session, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.new_session)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        cli = view.findViewById(R.id.cli)
        cwd = view.findViewById(R.id.cwd)
        name = view.findViewById(R.id.name)
        folder = view.findViewById(R.id.folder)
        error = view.findViewById(R.id.error)

        view.findViewById<View>(R.id.create).setOnClickListener { create() }
        load()
    }

    private fun load() {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).state()
                }
                state = s
                cwd.setText(s.home)
                val installed = s.clis.filter { it.installed }.ifEmpty { s.clis }
                if (installed.isEmpty()) {
                    error.setText(R.string.no_clis)
                    return@launch
                }
                cli.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    installed.map { it.label },
                )
                folderChoices.clear()
                folderChoices.add(null)
                folderChoices.addAll(s.folders)
                folder.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    folderChoices.map { it?.name ?: getString(R.string.ungrouped) },
                )
            } catch (e: Exception) {
                error.text = e.message ?: getString(R.string.load_failed)
            }
        }
    }

    private fun create() {
        val s = state ?: return
        val installed = s.clis.filter { it.installed }.ifEmpty { s.clis }
        val cliId = installed.getOrNull(cli.selectedItemPosition)?.id ?: return
        val folderId = folderChoices.getOrNull(folder.selectedItemPosition)?.id
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        error.text = ""
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).createSession(
                        cli = cliId,
                        cwd = cwd.text.toString().ifBlank { s.home },
                        name = name.text.toString().trim(),
                        folder = folderId,
                    )
                }
                parentFragmentManager.popBackStack()
                act.showSession(serverId, id)
            } catch (e: Exception) {
                error.text = e.message ?: getString(R.string.create_failed)
            }
        }
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        fun newInstance(serverId: String) = NewSessionFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERVER, serverId) }
        }
    }
}
