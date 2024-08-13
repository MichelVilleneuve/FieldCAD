package com.michelvilleneuve.fieldcad

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class DrawingPage(
    @SerializedName("name") var name: String,
    @SerializedName("lines") var lines: MutableList<MyCustomDrawingView.Line> = mutableListOf(),
    @SerializedName("texts") var texts: MutableList<MyCustomDrawingView.Text> = mutableListOf(),
    @SerializedName("circles") var circles: MutableList<MyCustomDrawingView.Circle> = mutableListOf(),
    @SerializedName("arcs") var arcs: MutableList<MyCustomDrawingView.Arc> = mutableListOf(),
    @SerializedName("rectangles") var rectangles: MutableList<MyCustomDrawingView.Rectangle> = mutableListOf()
) : Serializable