package com.provacor.sathi.core.model

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val area get() = width.toLong() * height.toLong()
}

/**
 * One visible node on screen. Text of password fields is never captured.
 * [clickTarget] is the index of the element to press for this one: itself if
 * clickable, otherwise its nearest clickable ancestor (labels inside list rows).
 */
data class UiElement(
    val index: Int,
    val text: String?,
    val description: String?,
    val viewId: String?,
    val className: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val focused: Boolean,
    val bounds: Bounds,
    val clickTarget: Int?,
) {
    val label: String get() = listOfNotNull(text, description).joinToString(" ").trim()
}

/** What the agent sees of the current screen: a structured, minimal view, not a screenshot. */
data class ScreenState(
    val packageName: String?,
    val elements: List<UiElement>,
    val screenWidth: Int,
    val screenHeight: Int,
) {
    /** Changes whenever visible content or layout changes; used to verify an action had an effect. */
    val signature: Int by lazy {
        elements.fold(packageName.hashCode()) { acc, e -> 31 * acc + e.label.hashCode() * 7 + e.bounds.top }
    }

    fun element(index: Int?): UiElement? = index?.let { elements.getOrNull(it) }
}
