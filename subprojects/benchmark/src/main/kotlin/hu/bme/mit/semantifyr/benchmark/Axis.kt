/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

class Axis<T>(
    val name: String,
    private val labeler: (T) -> String,
) {
    fun label(value: T): String {
        return labeler(value)
    }

    override fun toString(): String {
        return name
    }
}

class Dimension<T>(
    val axis: Axis<T>,
    private val values: (Cell) -> List<T>,
) {
    fun expand(cell: Cell): List<Cell> {
        return values(cell).map {
            cell.with(axis, it)
        }
    }
}

class Cell private constructor(
    private val values: Map<Axis<*>, Any?>,
    val coordinates: Map<String, String>,
) {
    val key: String
        get() = coordinateKey(coordinates)

    operator fun <T> get(axis: Axis<T>): T {
        require(axis in values) { "Cell $key has no value on axis ${axis.name}" }
        @Suppress("UNCHECKED_CAST")
        return values[axis] as T
    }

    fun <T> with(axis: Axis<T>, value: T): Cell {
        require(axis.name !in coordinates) { "Axis ${axis.name} is declared twice" }
        return Cell(values + (axis to value), coordinates + (axis.name to axis.label(value)))
    }

    override fun toString(): String {
        return key
    }

    companion object {
        val EMPTY = Cell(emptyMap(), emptyMap())
    }
}

fun coordinateKey(coordinates: Map<String, String>): String {
    return coordinates.entries.joinToString("/") {
        "${it.key}=${it.value}"
    }
}

fun permute(dimensions: List<Dimension<*>>): List<Cell> {
    val cells = dimensions.fold(listOf(Cell.EMPTY)) { partialCells, dimension ->
        partialCells.flatMap {
            dimension.expand(it)
        }
    }
    val duplicates = cells.groupBy { it.key }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Axis values share a label: ${duplicates.first()}" }
    return cells
}
