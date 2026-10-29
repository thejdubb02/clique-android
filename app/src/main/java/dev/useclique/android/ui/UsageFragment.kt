package dev.useclique.android.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import dev.useclique.android.api.CliUsage
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.UsageWindow
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class UsageFragment : Fragment() {

    private lateinit var serverId: String
    private lateinit var scroller: View
    private lateinit var items: LinearLayout
    private lateinit var message: TextView
    private lateinit var error: TextView
    private lateinit var progress: ProgressBar
    private val pulses = ArrayList<ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverId = requireArguments().getString(ARG_SERVER)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_usage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.usage)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        scroller = view.findViewById(R.id.scroller)
        items = view.findViewById(R.id.items)
        message = view.findViewById(R.id.message)
        error = view.findViewById(R.id.error)
        progress = view.findViewById(R.id.progress)
        load()
    }

    override fun onDestroyView() {
        stopPulses()
        super.onDestroyView()
    }

    private fun load() {
        val act = activity as MainActivity
        val server = act.app.store.get(serverId) ?: return
        val token = act.app.store.token(serverId)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val usage = withContext(Dispatchers.IO) {
                    CliqueClient.forServer(server, token).usage()
                }
                if (!isAdded) return@launch
                progress.visibility = View.GONE
                if (usage.isEmpty()) {
                    message.visibility = View.VISIBLE
                } else {
                    bind(usage)
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

    private fun bind(usage: List<CliUsage>) {
        items.removeAllViews()
        stopPulses()
        val pad = dp(16f)
        for (entry in usage) {
            items.addView(cliTitle(entry.cli), rowLp(top = pad))
            // A failed refresh can still carry the last good windows: show both.
            for (window in entry.windows) {
                items.addView(windowRow(window), rowLp(top = dp(6f)))
            }
            if (entry.error.isNotBlank()) {
                val err = TextView(requireContext())
                err.text = getString(R.string.usage_error, entry.error)
                err.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                items.addView(err, rowLp())
            }
        }
    }

    private fun cliTitle(cli: String): TextView {
        val tv = TextView(requireContext())
        tv.text = cli
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.textSize = 16f
        return tv
    }

    private fun windowRow(window: UsageWindow): LinearLayout {
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.VERTICAL

        val label = TextView(requireContext())
        val resetText = untilReset(window.resetsAt)
        label.text = if (resetText.isNullOrBlank()) {
            getString(R.string.usage_label_percent, window.label, window.percent.toInt())
        } else {
            getString(R.string.usage_label_percent_reset, window.label, window.percent.toInt(), resetText)
        }
        label.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
        row.addView(label)

        val bar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progress = window.percent.toInt().coerceIn(0, 100)
        val colorRes = levelColor(window.percent)
        bar.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8f))
        lp.topMargin = dp(4f)
        row.addView(bar, lp)

        if (window.percent >= 90.0) pulse(bar)
        return row
    }

    private fun levelColor(percent: Double): Int = when {
        percent >= 90.0 -> R.color.error
        percent >= 75.0 -> R.color.waiting
        else -> R.color.alive
    }

    private fun pulse(bar: ProgressBar) {
        val scale = Settings.Global.getFloat(requireContext().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) return
        val animator = ObjectAnimator.ofFloat(bar, View.ALPHA, 1f, 0.4f, 1f)
        animator.duration = 1600
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()
        pulses.add(animator)
    }

    private fun stopPulses() {
        pulses.forEach { it.cancel() }
        pulses.clear()
    }

    private fun untilReset(resetsAt: String): String? {
        if (resetsAt.isBlank()) return null
        val then = try {
            ISO.parse(resetsAt)?.time ?: return null
        } catch (_: ParseException) {
            return null
        }
        val now = System.currentTimeMillis()
        if (then <= now) return getString(R.string.usage_resets_now)
        return getString(R.string.usage_resets_in, DateUtils.getRelativeTimeSpanString(then, now, DateUtils.MINUTE_IN_MILLIS))
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt()

    private fun rowLp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = top }

    companion object {
        private const val ARG_SERVER = "serverId"
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun newInstance(serverId: String) = UsageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERVER, serverId)
            }
        }
    }
}
