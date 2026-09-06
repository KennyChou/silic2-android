package cc.kennydev.silic2.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.data.model.AnimalCategory
import cc.kennydev.silic2.app.data.model.SoundClass
import cc.kennydev.silic2.app.ui.components.CategoryBadge
import cc.kennydev.silic2.app.ui.components.SoundClassBadge
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.ForestSurfaceVariant
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import cc.kennydev.silic2.app.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesFilterScreen(
    allClasses: List<SoundClass>,
    selectedIds: Set<Int>,
    onToggleClass: (Int) -> Unit,
    onSelectMultiple: (List<Int>) -> Unit,
    onDeselectMultiple: (List<Int>) -> Unit,
    onSetSelection: (Collection<Int>) -> Unit = {},
    onClearSelection: () -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AnimalCategory.ALL) }

    // 依大分類與搜尋字串雙重過濾
    val filteredList = remember(searchQuery, selectedCategory, allClasses) {
        allClasses.filter { soundClass ->
            val matchCategory = selectedCategory == AnimalCategory.ALL || soundClass.category == selectedCategory
            if (!matchCategory) return@filter false

            if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                soundClass.speciesName.lowercase().contains(q) ||
                soundClass.scientificName.lowercase().contains(q) ||
                soundClass.soundClass.lowercase().contains(q) ||
                soundClass.soundclassId.toString().contains(q)
            }
        }
    }

    // 計算各分類數量
    val birdCount = remember(allClasses) { allClasses.count { it.category == AnimalCategory.BIRD } }
    val frogCount = remember(allClasses) { allClasses.count { it.category == AnimalCategory.FROG } }
    val mammalCount = remember(allClasses) { allClasses.count { it.category == AnimalCategory.MAMMAL } }
    val otherCount = remember(allClasses) { allClasses.count { it.category == AnimalCategory.OTHER } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目標物種篩選 (${allClasses.size} 類)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = onClearSelection) {
                            Text("清空選取", color = Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkForestBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 搜尋列
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜尋中文名、學名或編號...", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SilicGreen) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextTertiary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SilicGreen,
                    unfocusedBorderColor = ForestSurface,
                    focusedContainerColor = ForestSurface,
                    unfocusedContainerColor = ForestSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 快速分類標籤列 (橫向滑動)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 全部
                CategoryTabChip(
                    category = AnimalCategory.ALL,
                    count = allClasses.size,
                    isSelected = selectedCategory == AnimalCategory.ALL,
                    onClick = {
                        selectedCategory = AnimalCategory.ALL
                        onSetSelection(allClasses.map { it.soundclassId })
                    }
                )
                // 鳥類
                CategoryTabChip(
                    category = AnimalCategory.BIRD,
                    count = birdCount,
                    isSelected = selectedCategory == AnimalCategory.BIRD,
                    onClick = {
                        selectedCategory = AnimalCategory.BIRD
                        onSetSelection(allClasses.filter { it.category == AnimalCategory.BIRD }.map { it.soundclassId })
                    }
                )
                // 蛙類
                CategoryTabChip(
                    category = AnimalCategory.FROG,
                    count = frogCount,
                    isSelected = selectedCategory == AnimalCategory.FROG,
                    onClick = {
                        selectedCategory = AnimalCategory.FROG
                        onSetSelection(allClasses.filter { it.category == AnimalCategory.FROG }.map { it.soundclassId })
                    }
                )
                // 哺乳類
                CategoryTabChip(
                    category = AnimalCategory.MAMMAL,
                    count = mammalCount,
                    isSelected = selectedCategory == AnimalCategory.MAMMAL,
                    onClick = {
                        selectedCategory = AnimalCategory.MAMMAL
                        onSetSelection(allClasses.filter { it.category == AnimalCategory.MAMMAL }.map { it.soundclassId })
                    }
                )
                // 其他
                if (otherCount > 0) {
                    CategoryTabChip(
                        category = AnimalCategory.OTHER,
                        count = otherCount,
                        isSelected = selectedCategory == AnimalCategory.OTHER,
                        onClick = {
                            selectedCategory = AnimalCategory.OTHER
                            onSetSelection(allClasses.filter { it.category == AnimalCategory.OTHER }.map { it.soundclassId })
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 快捷操作與狀態列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedIds.isEmpty()) "模式：聆聽全部聲音" else "已勾選 ${selectedIds.size} 種",
                    fontSize = 13.sp,
                    color = if (selectedIds.isEmpty()) SilicGreen else TealAccent,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val currentListIds = remember(filteredList) { filteredList.map { it.soundclassId } }
                    val isAllCurrentSelected = currentListIds.isNotEmpty() && currentListIds.all { selectedIds.contains(it) }

                    // 一鍵切換全選 / 取消全選當前清單
                    if (currentListIds.isNotEmpty()) {
                        AssistChip(
                            onClick = {
                                if (isAllCurrentSelected) {
                                    onDeselectMultiple(currentListIds)
                                } else {
                                    onSelectMultiple(currentListIds)
                                }
                            },
                            label = {
                                Text(
                                    text = if (isAllCurrentSelected) "取消全選" else "全選 (${currentListIds.size})",
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isAllCurrentSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isAllCurrentSelected) ForestSurfaceVariant else ForestSurface,
                                labelColor = if (isAllCurrentSelected) TextSecondary else SilicGreen,
                                leadingIconContentColor = if (isAllCurrentSelected) TextSecondary else SilicGreen
                            )
                        )
                    }

                    if (selectedIds.isNotEmpty()) {
                        AssistChip(
                            onClick = onClearSelection,
                            label = { Text("清空選取", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = ForestSurface,
                                labelColor = Color(0xFFFF8A80)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 物種清單
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredList, key = { it.soundclassId }) { soundClass ->
                    val isChecked = selectedIds.contains(soundClass.soundclassId)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ForestSurface, RoundedCornerShape(8.dp))
                            .clickable { onToggleClass(soundClass.soundclassId) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge(category = soundClass.category)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = soundClass.speciesName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SoundClassBadge(soundClass = soundClass.soundClass)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = soundClass.scientificName,
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                color = TextTertiary
                            )
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleClass(soundClass.soundclassId) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = SilicGreen,
                                checkmarkColor = DarkForestBg,
                                uncheckedColor = TextTertiary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryTabChip(
    category: AnimalCategory,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = "${category.emoji} ${category.displayName} ($count)",
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SilicGreen,
            selectedLabelColor = DarkForestBg,
            containerColor = ForestSurface,
            labelColor = TextSecondary
        )
    )
}
