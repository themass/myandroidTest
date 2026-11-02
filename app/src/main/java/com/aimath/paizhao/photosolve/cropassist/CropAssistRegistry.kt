package com.aimath.paizhao.photosolve.cropassist

import android.content.Context

/** Ordered list of all known [CropAssistProvider]s, plus which one is currently active. */
class CropAssistRegistry(context: Context) {
    private val appContext = context.applicationContext

    val manual: CropAssistProvider = ManualCropAssistProvider()
    val openCv: CropAssistProvider = OpenCvCropAssistProvider()
    val docQuadNet: CropAssistProvider = DocQuadNetCropAssistProvider(appContext)
    val companyMnn: CropAssistProvider = CompanyMnnCropAssistProvider(appContext)

    /** All providers that can return a suggested box (i.e. everything except ML Kit's whole-flow scan). */
    val all: List<CropAssistProvider> = listOf(manual, openCv, docQuadNet, companyMnn)

    fun byId(id: String): CropAssistProvider = all.firstOrNull { it.id == id } ?: manual
}

/** Persists which [CropAssistProvider] is active for the normal (non-comparison) flow. */
class CropAssistSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveProviderId(): String = prefs.getString(KEY_ACTIVE_PROVIDER, ManualCropAssistProvider().id)
        ?: ManualCropAssistProvider().id

    fun setActiveProviderId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, id).apply()
    }

    private companion object {
        const val PREFS_NAME = "crop_assist_settings"
        const val KEY_ACTIVE_PROVIDER = "active_provider_id"
    }
}
