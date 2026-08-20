package com.example.aniframeapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SavedTimingAdapter(
    private val items: List<Pair<String, String>>,
    private val onDelete: (Int) -> Unit,
    private val onSelectionChanged: (Set<Int>) -> Unit
) : RecyclerView.Adapter<SavedTimingAdapter.ViewHolder>() {

    private var pendingDeletePosition: Int = -1
    private val selectedPositions = mutableSetOf<Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvSavedTitle)
        val date: TextView = view.findViewById(R.id.tvSavedDate)
        val deleteButton: View = view.findViewById(R.id.btnDeleteTiming)
        val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_timing, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.title.text = items[position].first
        holder.date.text = items[position].second
        holder.itemView.setBackgroundColor(
            if (position == pendingDeletePosition) 0xFF16274D.toInt() else 0xFF2E4A8A.toInt()
        )
        holder.deleteButton.setOnClickListener { onDelete(position) }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedPositions.contains(position)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPositions.add(position)
            } else {
                selectedPositions.remove(position)
            }
            onSelectionChanged(selectedPositions.toSet())
        }
    }

    override fun getItemCount(): Int = items.size

    fun setPendingDelete(position: Int) {
        val old = pendingDeletePosition
        pendingDeletePosition = position
        if (old >= 0) notifyItemChanged(old)
        if (position >= 0) notifyItemChanged(position)
    }

    fun selectAll() {
        selectedPositions.clear()
        selectedPositions.addAll(0 until items.size)
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(selectedPositions.toSet())
    }

    fun deselectAll() {
        selectedPositions.clear()
        notifyItemRangeChanged(0, items.size)
        onSelectionChanged(selectedPositions.toSet())
    }

    fun getSelectedPositions(): Set<Int> = selectedPositions.toSet()
}
