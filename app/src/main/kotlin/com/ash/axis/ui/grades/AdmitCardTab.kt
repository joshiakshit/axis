package com.ash.axis.ui.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.AdmitCardEntry
import com.ash.axis.ui.BottomSpacer
import com.ash.core.ui.components.AppCard
import com.ash.core.ui.theme.AppDimens

@Composable
internal fun AdmitCardContent(
    data: GradesUiState,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(
            data.admitCards,
            key = { index, entry -> "${index}_${entry.subjectCode}_${entry.date}" },
        ) { _, entry ->
            AdmitCardEntryCard(entry)
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun AdmitCardEntryCard(entry: AdmitCardEntry) {
    AppCard {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            if (entry.date.isNotBlank()) {
                Text(
                    entry.date,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                entry.subjectName.ifBlank { entry.subjectCode.ifBlank { "Subject" } },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subjectCode.isNotBlank() && entry.subjectName.isNotBlank()) {
                Text(
                    entry.subjectCode,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (entry.fromTime.isNotBlank() || entry.toTime.isNotBlank()) {
                    DetailItem(
                        label = "Time",
                        value =
                            listOfNotNull(
                                entry.fromTime.takeIf { it.isNotBlank() },
                                entry.toTime.takeIf { it.isNotBlank() },
                            ).joinToString(" - "),
                    )
                }
                if (entry.room.isNotBlank()) {
                    DetailItem(label = "Room", value = entry.room)
                }
                if (entry.seat.isNotBlank()) {
                    DetailItem(label = "Seat", value = entry.seat)
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
) {
    Column {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
