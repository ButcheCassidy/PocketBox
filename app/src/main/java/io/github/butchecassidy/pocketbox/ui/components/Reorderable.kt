// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.butchecassidy.pocketbox.R

/**
 * Drag-to-reorder for a LazyColumn: drag a row's handle and it follows the finger; dropping it
 * reports one move (from → to). Items keep stable keys, so nothing else recomposes.
 *
 * [indexOffset] is the number of list items before the first reorderable row (headers).
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val indexOffset: Int,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var targetIndex: Int? = null

    fun start(index: Int) {
        draggingIndex = index
        targetIndex = index
        dragOffset = 0f
    }

    fun drag(deltaY: Float) {
        val from = draggingIndex ?: return
        dragOffset += deltaY
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.index == from + indexOffset } ?: return
        val center = current.offset + current.size / 2 + dragOffset
        val over = items.firstOrNull { center.toInt() in it.offset until it.offset + it.size && it.index >= indexOffset }
        targetIndex = (over?.index ?: (from + indexOffset)) - indexOffset
    }

    fun end() {
        val from = draggingIndex
        val to = targetIndex
        draggingIndex = null
        dragOffset = 0f
        if (from != null && to != null && from != to) onMove(from, to)
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, indexOffset: Int = 0, onMove: (Int, Int) -> Unit): ReorderState =
    remember(listState, indexOffset) { ReorderState(listState, indexOffset, onMove) }

/** Lifts the row being dragged and moves it with the finger. */
fun Modifier.reorderItem(state: ReorderState, index: Int): Modifier = graphicsLayer {
    if (state.draggingIndex == index) {
        translationY = state.dragOffset
        shadowElevation = DRAG_ELEVATION
    }
}

/** The ≡ handle that starts a drag. */
@Composable
fun DragHandle(state: ReorderState, index: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reorder)
    Icon(
        painterResource(R.drawable.ic_drag_handle),
        contentDescription = null,
        modifier = modifier
            .semantics { contentDescription = label }
            .pointerInput(state, index) {
                detectDragGestures(
                    onDragStart = { state.start(index) },
                    onDrag = { change, amount ->
                        change.consume()
                        state.drag(amount.y)
                    },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                )
            },
    )
}

private const val DRAG_ELEVATION = 16f
