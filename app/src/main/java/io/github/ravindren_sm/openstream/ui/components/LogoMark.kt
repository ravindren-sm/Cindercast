package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.R
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** The OpenStream icon, with the name beside it when [withName] is set. Not focusable. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 36.dp, withName: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_launcher), contentDescription = "OpenStream", modifier = Modifier.size(size))
        if (withName) Text("OpenStream", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
    }
}
