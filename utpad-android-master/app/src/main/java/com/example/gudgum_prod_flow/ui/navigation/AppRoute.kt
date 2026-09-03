package com.example.gudgum_prod_flow.ui.navigation

object AppRoute {
    const val WorkerLogin = "worker_login"
    const val PinReset = "pin_reset"
    const val Inwarding = "inwarding"
    const val Production = "production"
    const val Packing = "packing"
    const val Dispatch = "dispatch"
    const val Returns = "returns"
    const val ModuleSelector = "module_selector"
    // Packing-side inventory sub-screens (gated by 'packing' module permission).
    const val UpdateInventory       = "update_inventory"
    const val FinishedGoodsInventory = "finished_goods_inventory"
}
