package dev.useclique.android.ui

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.ApiException
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.store.Server

class EditServerFragment : Fragment() {

    private var serverId: String? = null
    private lateinit var name: EditText
    private lateinit var url: EditText
    private lateinit var caPem: EditText
    private lateinit var deviceName: EditText
    private lateinit var code: EditText
    private lateinit var pairStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = arguments?.getString(ARG_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_edit_server, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = activity as MainActivity
        val store = act.app.store
        val existing = serverId?.let { store.get(it) }

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(if (existing == null) R.string.add_server else R.string.edit_server)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        name = view.findViewById(R.id.name)
        url = view.findViewById(R.id.url)
        caPem = view.findViewById(R.id.ca_pem)
        deviceName = view.findViewById(R.id.device_name)
        code = view.findViewById(R.id.code)
        pairStatus = view.findViewById(R.id.pair_status)

        if (existing != null) {
            name.setText(existing.name)
            url.setText(existing.baseUrl)
            caPem.setText(existing.caPem)
        }
        deviceName.setText(defaultDeviceName())

        view.findViewById<View>(R.id.pair).setOnClickListener { pair() }
        view.findViewById<View>(R.id.save).setOnClickListener { save(token = store.token(existing?.id ?: "")) }
        val remove = view.findViewById<View>(R.id.remove)
        if (existing != null) {
            remove.visibility = View.VISIBLE
            remove.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.remove_confirm, existing.name.ifBlank { existing.baseUrl }))
                    .setPositiveButton(R.string.remove_server) { _, _ ->
                        store.remove(existing.id)
                        parentFragmentManager.popBackStack()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun draft(): Server {
        val store = (activity as MainActivity).app.store
        val n = name.text.toString().trim().ifBlank { urlHost() }
        val u = normalizeUrl(url.text.toString())
        val pem = caPem.text.toString().trim()
        val existing = serverId?.let { store.get(it) }
        return existing?.copy(name = n, baseUrl = u, caPem = pem)
            ?: store.newServer(n, u, pem)
    }

    private fun urlHost(): String {
        return try {
            normalizeUrl(url.text.toString()).substringAfter("://").substringBefore("/")
        } catch (_: Exception) {
            getString(R.string.app_name)
        }
    }

    private fun save(token: String?) {
        val act = activity as MainActivity
        val server = draft()
        if (server.baseUrl.isBlank() || server.baseUrl == "http://") {
            pairStatus.setText(R.string.pair_need_url)
            return
        }
        act.app.store.put(server, token)
        serverId = server.id
        parentFragmentManager.popBackStack()
    }

    private fun pair() {
        val codeText = code.text.toString().trim()
        if (codeText.isEmpty()) {
            pairStatus.setText(R.string.pair_need_code)
            return
        }
        val server = draft()
        if (server.baseUrl.isBlank() || server.baseUrl == "http://") {
            pairStatus.setText(R.string.pair_need_url)
            return
        }
        pairStatus.setText(R.string.checking)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val claim = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, null).claim(
                        codeText,
                        deviceName.text.toString().ifBlank { defaultDeviceName() },
                    )
                }
                val act = activity as MainActivity
                act.app.store.put(server, claim.token)
                serverId = server.id
                pairStatus.setText(R.string.pair_ok)
                act.showSessions(server.id)
            } catch (e: ApiException) {
                pairStatus.setText(R.string.pair_failed)
            } catch (e: Exception) {
                pairStatus.text = e.message ?: getString(R.string.pair_failed)
            }
        }
    }

    private fun defaultDeviceName(): String {
        val global = Settings.Global.getString(requireContext().contentResolver, Settings.Global.DEVICE_NAME)
        if (!global.isNullOrBlank()) return global
        return android.os.Build.MODEL
    }

    companion object {
        private const val ARG_ID = "id"

        fun newInstance(id: String?) = EditServerFragment().apply {
            arguments = Bundle().apply { putString(ARG_ID, id) }
        }

        fun normalizeUrl(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s.trimEnd('/')
        }
    }
}
