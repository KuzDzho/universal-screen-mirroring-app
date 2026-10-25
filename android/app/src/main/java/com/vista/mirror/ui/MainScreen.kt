package com.vista.mirror.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vista.mirror.discovery.DiscoveredTv
import com.vista.mirror.ui.theme.Black
import com.vista.mirror.ui.theme.Copper
import com.vista.mirror.ui.theme.DarkCard
import com.vista.mirror.ui.theme.DimWhite
import com.vista.mirror.ui.theme.MutedWhite
import com.vista.mirror.ui.theme.White

@Composable
fun MainScreen(
    discoveredTvs: List<DiscoveredTv>,
    activeTvId: String?,
    isMirroring: Boolean,
    onTvSelected: (DiscoveredTv) -> Unit,
    onStopMirroring: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // App title
        Text(
            text = "Трансляция",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = White,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Search status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "search_spin")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "angle"
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .size(12.dp)
                    .rotate(angle),
                strokeWidth = 1.8.dp,
                color = MutedWhite,
                trackColor = Color.Transparent
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Поиск телевизоров в сети Wi-Fi",
                fontSize = 14.sp,
                color = MutedWhite
            )
        }

        // TV List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(discoveredTvs, key = { it.id }) { tv ->
                val isSelected = activeTvId == tv.id && isMirroring
                TvListItem(
                    tv = tv,
                    isMirroring = isSelected,
                    onClick = {
                        if (isSelected) {
                            onStopMirroring()
                        } else {
                            onTvSelected(tv)
                        }
                    }
                )
            }
        }

        // Empty state
        if (discoveredTvs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ищем Sony Bravia, Samsung, LG и другие ТВ…",
                    fontSize = 14.sp,
                    color = DimWhite
                )
            }
        }
    }
}

@Composable
fun TvListItem(
    tv: DiscoveredTv,
    isMirroring: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMirroring) DarkCard else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isMirroring) Copper.copy(alpha = 0.2f) else DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMirroring) Icons.Default.CastConnected else Icons.Default.Tv,
                contentDescription = null,
                tint = if (isMirroring) Copper else White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tv.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = White
            )

            AnimatedVisibility(visible = isMirroring) {
                Text(
                    text = "Дублируется экран и звук",
                    fontSize = 13.sp,
                    color = Copper,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            AnimatedVisibility(visible = !isMirroring && tv.model.isNotEmpty()) {
                Text(
                    text = tv.model,
                    fontSize = 13.sp,
                    color = DimWhite,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
