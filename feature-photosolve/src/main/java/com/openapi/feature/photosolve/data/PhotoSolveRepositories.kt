package com.openapi.feature.photosolve.data

/**
 * 拍照解题后端的注入点，和 ChatDependencies / RolesDependencies 同一种接法。
 *
 * 默认是 [MockPhotoSolveRepository]。真实后端就绪后，在 Application.onCreate 里调用
 * [install] 换成真实实现即可，ViewModel 和界面都不用改。
 */
object PhotoSolveRepositories {
    @Volatile
    private var repository: PhotoSolveRepository = MockPhotoSolveRepository()

    fun install(repository: PhotoSolveRepository) {
        this.repository = repository
    }

    fun current(): PhotoSolveRepository = repository
}
