package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_flavors")
data class CachedFlavorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val recipeId: String? = null,
    val active: Boolean = true,
    val yieldThreshold: Double? = null,
    val shelfLifeDays: Int? = null,
    /** Gums in one box of this flavour. 15 for everything before packing variants. */
    val unitsPerBox: Int = 15,
    /** Set when this row is a packing variant of another flavour; null for a base flavour. */
    val parentFlavorId: String? = null,
) {
    /** A variant exists only as a box format of its parent — it is never produced. */
    val isPackingVariant: Boolean get() = parentFlavorId != null
}
