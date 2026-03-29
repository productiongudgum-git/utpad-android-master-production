package com.example.gudgum_prod_flow.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import kotlinx.coroutines.android.awaitFrame

/**
 * A reusable searchable dropdown composable that wraps Material3 ExposedDropdownMenuBox.
 * Includes: search/filter field, filtered items, and an optional "Add New" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    selectedLabel: String? = null,
    placeholder: String = "Select...",
    label: String? = null,
    onAddNewClick: (() -> Unit)? = null,
    addNewLabel: String = "Add New",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { itemLabel(it).contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            awaitFrame()
            searchFieldFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel ?: selectedItem?.let(itemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(placeholder, color = UtpadTextSecondary) },
            label = label?.let { { Text(it) } },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UtpadPrimary,
                unfocusedBorderColor = UtpadOutline,
                focusedContainerColor = UtpadBackground,
                unfocusedContainerColor = UtpadSurface,
                focusedTextColor = UtpadTextPrimary,
                unfocusedTextColor = UtpadTextPrimary,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
        )

        // Use a focusable popup so the embedded search field can receive IME focus.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
            modifier = Modifier
                .exposedDropdownSize(matchTextFieldWidth = true)
                .heightIn(max = 340.dp),
            properties = PopupProperties(focusable = true),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...", color = UtpadTextSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = UtpadTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .focusRequester(searchFieldFocusRequester)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UtpadPrimary,
                    unfocusedBorderColor = UtpadOutline,
                    focusedContainerColor = UtpadBackground,
                    unfocusedContainerColor = UtpadSurface,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            if (filteredItems.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No results found", color = UtpadTextSecondary) },
                    onClick = {},
                    enabled = false,
                )
            } else {
                filteredItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item)) },
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                            searchQuery = ""
                        },
                    )
                }
            }

            if (onAddNewClick != null) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = UtpadPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(addNewLabel, color = UtpadPrimary)
                        }
                    },
                    onClick = {
                        expanded = false
                        searchQuery = ""
                        onAddNewClick()
                    },
                )
            }
        }
    }
}
