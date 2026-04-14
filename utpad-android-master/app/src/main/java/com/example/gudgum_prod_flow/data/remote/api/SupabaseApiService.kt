package com.example.gudgum_prod_flow.data.remote.api

import com.example.gudgum_prod_flow.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApiService {

    // ── Flavors / SKUs (gg_flavors) ────────────────────────────────
    @GET("rest/v1/gg_flavors")
    suspend fun getGgFlavors(
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,name,code,active,recipe_id,yield_threshold,shelf_life_days",
        @Query("order") order: String = "name.asc",
    ): Response<List<FlavorDto>>

    // ── Recipe Lines (BOM) — recipe_lines + gg_ingredients join ────
    @GET("rest/v1/recipe_lines")
    suspend fun getRecipeLines(
        @Query("recipe_id") recipeId: String, // e.g. "eq.<uuid>"
        @Query("select") select: String = "recipe_id,ingredient_id,qty,ingredient:gg_ingredients(id,name,default_unit)",
    ): Response<List<RecipeLineDto>>

    // ── Ingredients (gg_ingredients) ───────────────────────────────
    @GET("rest/v1/gg_ingredients")
    suspend fun getGgIngredients(
        @Query("select") select: String = "id,name,default_unit",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgIngredientDto>>

    @POST("rest/v1/gg_ingredients")
    suspend fun insertGgIngredient(
        @Body request: GgIngredientInsertRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<GgIngredientDto>>

    // ── Recipes (gg_recipes) ───────────────────────────────────────
    @GET("rest/v1/gg_recipes")
    suspend fun getGgRecipe(
        @Query("flavor_id") flavorId: String, // "eq.<uuid>"
        @Query("select") select: String = "id,title,code,flavor_id,yield_factor,tolerance_pct,primary_ingredient_id,is_active",
    ): Response<List<GgRecipeDto>>

    // ── Vendors (gg_vendors) ───────────────────────────────────────
    @GET("rest/v1/gg_vendors")
    suspend fun getGgVendors(
        @Query("select") select: String = "id,name,phone",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgVendorDto>>

    @POST("rest/v1/gg_vendors")
    suspend fun insertGgVendor(
        @Body request: GgVendorInsertRequest,
        @Header("Prefer") prefer: String = "return=representation",
    ): Response<List<GgVendorDto>>

    // ── Users (gg_users) ───────────────────────────────────────────
    @GET("rest/v1/gg_users")
    suspend fun getGgUserByPhone(
        @Query("mobile_number") mobileNumber: String, // "eq.{phone}"
        @Query("role") role: String = "eq.worker",
        @Query("active") active: String = "eq.true",
        @Query("select") select: String = "id,mobile_number,name,role,modules,active",
    ): Response<List<GgUserDto>>

    // ── Customers (gg_customers) ───────────────────────────────────
    @GET("rest/v1/gg_customers")
    suspend fun getGgCustomers(
        @Query("select") select: String = "id,name,contact_person,phone",
        @Query("order") order: String = "name.asc",
    ): Response<List<GgCustomerDto>>

    // ── Production Batches (production_batches) ────────────────────
    @POST("rest/v1/production_batches")
    suspend fun insertProductionBatch(
        @Body request: SubmitProductionBatchRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @GET("rest/v1/production_batches")
    suspend fun findProductionBatch(
        @Query("batch_code") batchCode: String,
        @Query("flavor_id") flavorId: String,
        @Query("select") select: String = "id,batch_code,sku_id,flavor_id,recipe_id,production_date,worker_id,status,planned_yield,actual_yield,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 1,
    ): Response<List<ProductionBatchDto>>

    @PATCH("rest/v1/production_batches")
    suspend fun updateProductionBatch(
        @Query("id") id: String,
        @Body request: SubmitProductionBatchRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @GET("rest/v1/production_batches")
    suspend fun getOpenBatches(
        @Query("status") status: String = "eq.open",
        @Query("select") select: String = "id,batch_code,sku_id,flavor_id,recipe_id,production_date,planned_yield,actual_yield,status,flavor:gg_flavors!production_batches_flavor_id_fkey(id,name,code)",
        @Query("order") order: String = "production_date.desc",
    ): Response<List<ProductionBatchDto>>

    @GET("rest/v1/production_batches")
    suspend fun getPackedBatches(
        @Query("status") status: String = "eq.packed",
        @Query("select") select: String = "id,batch_code,sku_id,flavor_id,recipe_id,production_date,planned_yield,actual_yield,status,flavor:gg_flavors!production_batches_flavor_id_fkey(id,name,code)",
        @Query("order") order: String = "production_date.desc",
    ): Response<List<ProductionBatchDto>>

    // ── Packing Sessions (packing_sessions) ────────────────────────
    @POST("rest/v1/packing_sessions")
    suspend fun insertPackingSession(
        @Body request: SubmitPackingSessionRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @GET("rest/v1/packing_sessions")
    suspend fun findPackingSession(
        @Query("batch_code") batchCode: String,
        @Query("flavor_id") flavorId: String,
        @Query("session_date") sessionDate: String,
        @Query("select") select: String = "id,batch_code,flavor_id,session_date,worker_id,boxes_packed,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 1,
    ): Response<List<PackingSessionDto>>

    @PATCH("rest/v1/packing_sessions")
    suspend fun updatePackingSession(
        @Query("id") id: String,
        @Body request: SubmitPackingSessionRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Dispatch Events (dispatch_events) ──────────────────────────
    @POST("rest/v1/dispatch_events")
    suspend fun insertDispatchEvent(
        @Body request: SubmitDispatchEventRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    @GET("rest/v1/dispatch_events")
    suspend fun getDispatchedBatches(
        @Query("select") select: String = "batch_code,sku_id,boxes_dispatched,dispatch_date,customer_name,sku:gg_flavors(name)",
        @Query("order") order: String = "dispatch_date.desc",
        @Query("limit") limit: Int = 50,
    ): Response<List<DispatchedBatchDto>>

    // ── Returns Events (returns_events) ────────────────────────────
    @POST("rest/v1/returns_events")
    suspend fun insertReturnEvent(
        @Body request: SubmitReturnEventRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Inwarding (gg_inwarding) ───────────────────────────────────
    @POST("rest/v1/gg_inwarding")
    suspend fun insertGgInwarding(
        @Body request: GgInwardingRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Inventory (for Dispatch stock check) ───────────────────────
    @GET("rest/v1/inventory_finished_goods")
    suspend fun getFinishedGoodsStock(
        @Query("sku_id") skuId: String, // e.g. "eq.<uuid>"
        @Query("select") select: String = "sku_id,batch_code,boxes_available,boxes_returned",
        @Query("boxes_available") boxesFilter: String = "gt.0",
    ): Response<List<Map<String, Any>>>

    // ── Invoices (gg_invoices) — for FIFO dispatch ─────────────────
    @GET("rest/v1/gg_invoices")
    suspend fun getActiveInvoices(
        @Query("is_dispatched") isDispatched: String = "eq.false",
        @Query("order") order: String = "created_at.desc",
        @Query("select") select: String = "*",
    ): Response<List<InvoiceDto>>

    // ── Production batches by flavor for FIFO allocation ──────────
    @GET("rest/v1/production_batches")
    suspend fun getProductionBatchesByFlavor(
        @Query("flavor_id") flavorId: String,
        @Query("order") order: String = "production_date.asc",
        @Query("select") select: String = "id,batch_code,flavor_id,production_date,expected_boxes,expected_units",
    ): Response<List<ProductionBatchFifoDto>>

    // ── Update invoice status ──────────────────────────────────────
    @PATCH("rest/v1/gg_invoices")
    suspend fun updateInvoiceStatus(
        @Query("id") invoiceId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Update inventory finished goods ────────────────────────────
    @PATCH("rest/v1/inventory_finished_goods")
    suspend fun updateInventory(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>

    // ── Patch production_batches stock after FIFO dispatch ─────────
    @PATCH("rest/v1/production_batches")
    suspend fun patchProductionBatch(
        @Query("id") id: String,
        @Body body: PatchProductionBatchRequest,
        @Header("Prefer") prefer: String = "return=minimal",
    ): Response<Unit>
}
