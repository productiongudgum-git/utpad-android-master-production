package com.example.gudgum_prod_flow.data.session

object WorkerIdentityStore {
    @Volatile
    var workerId: String = "mobile-worker"

    @Volatile
    var workerName: String = "Mobile Worker"

    @Volatile
    var workerRole: String = "Worker"

    fun setIdentity(userId: String, phone: String, label: String, role: String) {
        workerId = userId
        workerName = label
        workerRole = role
    }

    fun clear() {
        workerId = "mobile-worker"
        workerName = "Mobile Worker"
        workerRole = "Worker"
    }
}
