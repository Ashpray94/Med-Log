package com.suryaprakash.medlog.pictogram

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.suryaprakash.medlog.clinical.Problem

/** A problem's icon from the sprite sheets. Decorative for screen readers: the word next to it carries the meaning. */
@Composable
fun ProblemPicture(problem: Problem?, size: Dp, modifier: Modifier = Modifier) = SpriteIcon(problem?.id, size, modifier)

@Composable
fun SpriteIcon(id: String?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp = remember(id, px) { Sprites.bitmap(ctx, id, px)?.asImageBitmap() }
    if (bmp != null) Image(bmp, null, modifier.size(size).clearAndSetSemantics { })
    else Box(modifier.size(size))
}

/** Icons for things that aren't symptoms (medicine, water, food...): mapped to the closest sprite. */
@Composable
fun Picture(region: String, glyph: String, size: Dp, modifier: Modifier = Modifier) {
    val id = when (glyph) {
        "pill" -> "side_effect"; "glass" -> "dry_mouth"; "plate" -> "no_appetite"; "beat" -> "palpitations"
        "block" -> "constipation"; "question" -> "confusion"; else -> "confusion"
    }
    SpriteIcon(id, size, modifier)
}
