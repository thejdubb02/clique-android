package dev.useclique.android.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.SessionDiff

class DiffFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var sessionId: String
    private lateinit var scroller: View
    private lateinit var diffText: TextView
    private lateinit var note: TextView
    private lateinit var message: TextView
    private lateinit var error: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
        sessionId = requireArguments().getString(ARG_SESSION)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_diff, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.diff)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        scroller = view.findViewById(R.id.scroller)
        diffText = view.findViewById(R.id.diff_text)
        note = view.findViewById(R.id.note)
        message = view.findViewById(R.id.message)
        error = view.findViewById(R.id.error)
        progress = view.findViewById(R.id.progress)
        diffText.typeface = Typeface.MONOSPACE
        diffText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        load()
    }

    private fun load() {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val diff = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).diff(sessionId)
                }
                if (!isAdded) return@launch
                progress.visibility = View.GONE
                show(diff)
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

    private fun show(diff: SessionDiff) {
        val footnote = if (diff.repo) footnote(diff.truncated, diff.untrackedHidden) else null
        when {
            !diff.repo -> {
                message.text = getString(R.string.diff_not_repo)
                message.visibility = View.VISIBLE
            }
            diff.empty -> {
                message.text = if (footnote == null) {
                    getString(R.string.diff_empty)
                } else {
                    getString(R.string.diff_empty) + "\n" + footnote
                }
                message.visibility = View.VISIBLE
            }
            else -> {
                diffText.text = colorDiff(diff.diff)
                if (footnote == null) {
                    note.visibility = View.GONE
                } else {
                    note.text = footnote
                    note.visibility = View.VISIBLE
                }
                scroller.visibility = View.VISIBLE
            }
        }
    }

    private fun footnote(truncated: Boolean, hidden: Int): String? {
        if (!truncated && hidden <= 0) return null
        val sb = StringBuilder()
        if (truncated) sb.append(getString(R.string.diff_truncated))
        if (hidden > 0) {
            if (truncated) sb.append(", ")
            sb.append(getString(R.string.diff_untracked, hidden))
        }
        return sb.toString()
    }

    private fun colorDiff(diff: String): SpannableStringBuilder {
        val added = ContextCompat.getColor(requireContext(), R.color.diff_added)
        val removed = ContextCompat.getColor(requireContext(), R.color.diff_removed)
        val accent = ContextCompat.getColor(requireContext(), R.color.accent)
        val plain = ContextCompat.getColor(requireContext(), R.color.text)
        val sb = SpannableStringBuilder()
        val lines = diff.split('\n')
        for ((i, line) in lines.withIndex()) {
            val start = sb.length
            sb.append(line)
            if (i < lines.lastIndex) sb.append('\n')
            val color = when {
                line.startsWith("+++") || line.startsWith("---") -> plain
                line.startsWith("+") -> added
                line.startsWith("-") -> removed
                line.startsWith("@@") -> accent
                else -> plain
            }
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    companion object {
        private const val ARG_SERVER = "serverId"
        private const val ARG_SESSION = "sessionId"

        fun newInstance(serverId: String, sessionId: String) = DiffFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERVER, serverId)
                putString(ARG_SESSION, sessionId)
            }
        }
    }
}
