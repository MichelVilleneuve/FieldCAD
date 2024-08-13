package com.michelvilleneuve.fieldcad

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PageAdapter(private val pages: MutableList<DrawingPage>) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val drawingView: MyCustomDrawingView = itemView.findViewById(R.id.drawingView)

        fun bind(page: DrawingPage) {
            // Bind the page content to the view
            drawingView.lines = page.lines
            drawingView.texts = page.texts
            drawingView.circles = page.circles
            drawingView.arcs = page.arcs
            drawingView.rectangles = page.rectangles

            drawingView.invalidate()

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_main, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size
}
