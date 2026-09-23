package dev.useclique.android.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.NotesOutline

class NotesFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var sessionId: String
    private lateinit var scroller: View
    private lateinit var items: LinearLayout
    private lateinit var message: TextView
    private lateinit var error: TextView
    private lateinit var progress: ProgressBar
    private var outline: NotesOutline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
        sessionId = requireArguments().getString(ARG_SESSION)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_notes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.notes)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        scroller = view.findViewById(R.id.scroller)
        items = view.findViewById(R.id.items)
        message = view.findViewById(R.id.message)
        error = view.findViewById(R.id.error)
        progress = view.findViewById(R.id.progress)
        load()
    }

    private fun load() {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).getNotes(sessionId)
                }
                if (!isAdded) return@launch
                progress.visibility = View.GONE
                outline = notes
                if (notes.items.isEmpty()) {
                    message.visibility = View.VISIBLE
                } else {
                    bind(notes)
                    scroller.visibility = View.VISIBLE
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

    private fun bind(notes: NotesOutline) {
        items.removeAllViews()
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            resources.displayMetrics,
        ).toInt()
        val textColor = ContextCompat.getColor(requireContext(), R.color.text)
        val tint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent))
        val surface = ContextCompat.getColor(requireContext(), R.color.surface)
        for (item in notes.items) {
            val box = CheckBox(requireContext())
            box.text = item.text
            box.setTextColor(textColor)
            box.buttonTintList = tint
            box.setBackgroundColor(surface)
            box.setPadding(pad, pad, pad, pad)
            box.isChecked = item.done
            box.setOnCheckedChangeListener { _, checked ->
                if (item.done == checked) return@setOnCheckedChangeListener
                item.done = checked
                push(notes.rawItems)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = 1
            items.addView(box, lp)
        }
    }

    /* Not tied to the view. Leaving the screen is when a just-flipped box
       still has to reach the panel, same as a draft saved on the way out. */
    private fun push(rawItems: JSONArray) {
        val act = activity as? MainActivity ?: return
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        val snapshot = JSONArray(rawItems.toString())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CliqueClient.forServer(server, token).postNotes(sessionId, snapshot)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        private const val ARG_SESSION = "sessionId"

        fun newInstance(serverId: String, sessionId: String) = NotesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERVER, serverId)
                putString(ARG_SESSION, sessionId)
            }
        }
    }
}
