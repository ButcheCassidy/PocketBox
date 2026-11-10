// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.ArtShape
import io.github.butchecassidy.pocketbox.domain.model.ColorSource
import io.github.butchecassidy.pocketbox.domain.model.ThemeMode
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import io.github.butchecassidy.pocketbox.ui.theme.artShapeFor
import io.github.butchecassidy.pocketbox.ui.theme.color.Hct

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, body: String? = null, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.selectable(selected = checked, enabled = enabled, role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
fun LinkRow(title: String, body: String? = null, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(painterResource(it), null) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A row of mutually exclusive choices as an expressive segmented button row. */
@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.one)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = spacing.one))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
    }
}

/** A labelled slider that saves when the finger lifts. */
@Composable
fun SliderRow(
    title: @Composable (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.one)) {
        Text(title(current), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

/**
 * Theme mode, AMOLED, colour source and custom colour, and the album-art shape. Shared by onboarding
 * and Settings → Appearance so both look and behave the same.
 */
@Composable
fun AppearanceControls(settings: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    ChoiceRow(
        stringResource(R.string.theme),
        listOf(
            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
            ThemeMode.LIGHT to stringResource(R.string.theme_light),
            ThemeMode.DARK to stringResource(R.string.theme_dark),
        ),
        settings.themeMode,
    ) { mode -> update { it.copy(themeMode = mode) } }
    SwitchRow(
        stringResource(R.string.amoled_black),
        settings.amoledBlack,
        { on -> update { it.copy(amoledBlack = on) } },
        body = stringResource(R.string.amoled_black_body),
    )
    ChoiceRow(
        stringResource(R.string.color_source),
        listOf(
            ColorSource.DYNAMIC to stringResource(R.string.color_dynamic),
            ColorSource.ALBUM_ART to stringResource(R.string.color_album),
            ColorSource.CUSTOM to stringResource(R.string.color_custom),
        ),
        settings.colorSource,
    ) { source -> update { it.copy(colorSource = source) } }
    if (settings.colorSource == ColorSource.CUSTOM) {
        ColorPicker(settings.customSeedColor) { color -> update { it.copy(customSeedColor = color) } }
    }
    SectionHeader(stringResource(R.string.art_shape))
    ArtShapePicker(settings.artShape) { shape -> update { it.copy(artShape = shape) } }
}

/** Preset swatches plus hue and tone sliders (HCT, so every pick yields a usable palette). */
@Composable
fun ColorPicker(color: Int, onPick: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    val (startHue, _, startTone) = remember(color) { Hct.fromArgb(color) }
    var hue by remember(color) { mutableFloatStateOf(startHue.toFloat()) }
    var tone by remember(color) { mutableFloatStateOf(startTone.toFloat().coerceIn(MIN_TONE, MAX_TONE)) }
    fun pick() = onPick(Hct.toArgb(hue.toDouble(), PICKER_CHROMA, tone.toDouble()))
    Column(Modifier.padding(horizontal = spacing.screen)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.one), verticalArrangement = Arrangement.spacedBy(spacing.one)) {
            swatches.forEach { swatch ->
                val selected = swatch == color
                Box(
                    Modifier
                        .size(LocalSizes.current.touchTarget)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .padding(spacing.half)
                        .semantics { contentDescription = "#" + Integer.toHexString(swatch).takeLast(6) }
                        .selectable(selected = selected, role = Role.RadioButton) { onPick(swatch) },
                ) {
                    Surface(color = Color(swatch), shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
                }
            }
        }
        Text(stringResource(R.string.hue), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = spacing.two))
        Slider(value = hue, onValueChange = { hue = it }, onValueChangeFinished = ::pick, valueRange = 0f..359f)
        Text(stringResource(R.string.tone), style = MaterialTheme.typography.labelLarge)
        Slider(value = tone, onValueChange = { tone = it }, onValueChangeFinished = ::pick, valueRange = MIN_TONE..MAX_TONE)
        Box(Modifier.fillMaxWidth().padding(vertical = spacing.one), contentAlignment = Alignment.Center) {
            Surface(
                color = Color(Hct.toArgb(hue.toDouble(), PICKER_CHROMA, tone.toDouble())),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().size(LocalSizes.current.touchTarget),
            ) {}
        }
    }
}

@Composable
private fun ArtShapePicker(selected: ArtShape, onPick: (ArtShape) -> Unit) {
    val spacing = LocalSpacing.current
    val size = LocalSizes.current.touchTarget + spacing.two
    FlowRow(
        Modifier.padding(horizontal = spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(spacing.two),
        verticalArrangement = Arrangement.spacedBy(spacing.two),
    ) {
        ArtShape.entries.forEach { shape ->
            val isSelected = shape == selected
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                shape = artShapeFor(shape),
                modifier = Modifier
                    .size(size)
                    .semantics { contentDescription = shape.name.lowercase().replace('_', ' ') }
                    .selectable(selected = isSelected, role = Role.RadioButton) { onPick(shape) },
            ) {}
        }
    }
}

private const val PICKER_CHROMA = 48.0
private const val MIN_TONE = 30f
private const val MAX_TONE = 70f
private val swatches = listOf(
    0xFFE07A3F, 0xFFB3261E, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB,
    0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFFDD835, 0xFF6D4C41,
).map { it.toInt() }
